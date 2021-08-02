package c4

import c4.C4ParUtils.C4ExecutionContext
import spire.implicits.cfor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag

/** A fragmented array.
 *
 * It's backed by a single array, but only sections of the array are in use.
 *
 * Fragmented arrays are usually the product of some parallelized filter operation.
 * The input array got split into slices and processed in parallel.
 * However because it couldn't know up front how many elements would survive the filter,
 * it just allocated the same number of elements as the original array.
 * Each parallel process is assigned the same chunk in the parallel array which it fills
 * up from the beginning of its chunk.
 * This leaves a contiguous chunk of values followed by dead space.
 *
 * TODO ascii diagram
 *
 * The advantage of this approach is that each worker can independently fill directly into
 * the final array rather than first writing into local buffers which later get flattened
 * back into a final array. This effectively causes an extra pass over the array.
 * Often a filter step is followed by a `map` anyway, and the map step can be used to
 * simultaneously map the values and defrag back into a simple array as we know exactly how
 * many elements we have.
 *
 * Random access in a fragmented array is O(p) where p is the number of partitions
 * as it has to hop over the dead space.
 * Usually p is from 4-16 so this is effectively constant time.
 * We don't use random access on collections much anyway, usually we iterate start to finish,
 * and with iteration we only make p jumps across the entire iteration.
 *
 * The main trade off for a fragmented array is the extra wasted space.
 *
 * Internally the state of the fragmented array is:
 * - the underlying array
 * - a collection of (start, stop) regions defining where the chunks are within that space
 *
 * Note that the ec is built into the frag array.
 * This is to avoid ambiguity about the degree of parallelism as that's driven by the number
 * of internal partitions.
 * For example we want to avoid: list.filterFrag(ec1).map(ec2)
 * where the ec1 and ec2 have different parallelism.
 */
class FragArray[A: ClassTag] private(array: Array[A], partitions: List[(Int, Int)], ec: C4ExecutionContext) {

  /** As mentioned in the docstring, a map operation simultaneously maps and defrags
   *
   * meaning we can return a simple Array rather than a fragged array.
   *
   * Internally the fragments are still used to drive the parallelism.
   *
   * TODO - being lazy and just making the par versions of this the standard one
   *   That's the whole point of this collection
   */
  def map[B: ClassTag](f: A => B): Array[B] = {
    val mappedLength = this.length

    val mapped = Array.ofDim[B](mappedLength)

    val mappedPartitions = align(this.partitions)

    val parallelized = Future.traverse(this.partitions.zip(mappedPartitions)) {
      case ((thisStart, thisStop), (mappedStart, _)) =>
        Future {
          val partitionWidth = thisStop - thisStart
          cfor(0)(_ < partitionWidth, _ + 1) { i =>
            mapped(mappedStart + i) = f(array(thisStart + i))
          }
        }(ec.threadPool)
    }

    Await.result(parallelized, Duration.Inf)

    mapped
  }

  /** Filters and partially defrags the collection.
   *
   * We know how many elements are in the current fragmented collection
   * but don't know how many there'll be after the _next_ filter.
   *
   * We can at least reduce the memory footprint to the size of the current
   * collection defragmented.
   */
  def filter(pred: A => Boolean): FragArray[A] = {
    val refiltered = Array.ofDim[A](this.length)

    val refilteredCompletePartitions = align(this.partitions)

    // See notes in Ops.filterFrag - implementation is similar
    val refilteredPartitionsFut = Future.traverse(this.partitions.zip(refilteredCompletePartitions)) {
      case ((thisStart, thisStop), (refilteredStart, _)) =>
        Future {
          val partitionWidth = thisStop - thisStart
          var nextInsert = refilteredStart
          cfor(0)(_ < partitionWidth, _ + 1) { i =>
            val element = array(thisStart + i)
            if (pred(element)) {
              refiltered(nextInsert) = element
              nextInsert += 1
            }
          }
          (refilteredStart, nextInsert)
        }(ec.threadPool)
    }

    val refilteredPartitions = Await.result(refilteredPartitionsFut, Duration.Inf)

    new FragArray[A](refiltered, refilteredPartitions, ec)
  }

  val length: Int = partitions.map { case (start, stop) => stop - start }.sum

  /** Defrags this into a simple array
   *
   * Note that if you plan to map it anyway, you can map and defrag simultaneously, e.g.
   *
   *   myArray.filterFrag(p).toArray.map(f)
   *
   *     is the same as
   *
   *  myArray.filterFrag(p).map(f)
   *
   *  and the latter performs better by not creating an intermediate collection
   */
  def toArray: Array[A] = map(a => a)

  /** Converts fragmented partitions into aligned partitions that sit "back to back".
   *
   * e.g.
   *   align(List(
   *     (3, 5), (7, 10), (14, 19)
   *   ))
   *
   *   Produces:
   *
   *  List(
   *   (0, 2), (2, 5), (5, 10)
   *  )
   */
  private def align(fragmentedPartitions: List[(Int, Int)]): List[(Int, Int)] = {
    val seed: (Int, List[(Int, Int)]) = (0, List.empty)
    val (_, alignedPartitionsReversed) = fragmentedPartitions.foldLeft(seed) {
      case ((alignedStartPoint, alignedPartitionsAcc), (unalignedPartitionsStart, unalignedPartitionsStop)) =>
        val partitionWidth = unalignedPartitionsStop - unalignedPartitionsStart
        val nextAlignedPartitionStart = alignedStartPoint + partitionWidth
        (nextAlignedPartitionStart, (alignedStartPoint, nextAlignedPartitionStart) :: alignedPartitionsAcc)
    }

    alignedPartitionsReversed.reverse
  }
}

object FragArray {
  implicit class Ops[A: ClassTag](array: Array[A]) {
    def filterFrag(pred: A => Boolean)(implicit ec: C4ExecutionContext): FragArray[A] = {
      val filtered = Array.ofDim[A](array.length)
      val partitions = C4ParUtils.partitions(array.length, ec.parallelism)

      // Simultaneously fill the `filtered` array and capture what areas of it had data put into it.
      // The array is divided into partitions and each partition is processed in parallel.
      // Each time there's an insert we update the position representing the space we've used
      // which ends up becoming the `stop` p
      val filteredPartitionsFut: Future[List[(Int, Int)]] = Future.traverse(partitions) { case (start, stop) =>
        Future {
          var nextInsert = start
          cfor(start)(_ < stop, _ + 1) { i =>
            val element = array(i)
            if (pred(element)) {
              filtered(nextInsert) = element
              nextInsert += 1
            }
          }
          (start, nextInsert)
        }(ec.threadPool)
      }

      val filteredPartitions = Await.result(filteredPartitionsFut, Duration.Inf)

      new FragArray[A](filtered, filteredPartitions, ec)
    }
  }
}
