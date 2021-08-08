package c4

import scala.reflect.ClassTag
import scala.collection.mutable.ArrayBuffer
import spire.implicits.cfor
import C4ParUtils.{C4ExecutionContext, flattenBuffers, partitions}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

/** An attempted optimization over Fuse which tries to be more efficient
 * by short-circuiting out faster in the case that there's no data.
 *
 * The rationale was that it in `Fuse`, there's some inefficiency:
 * - an extra boostrapping stage `a => Some(a)` is added to make it fit the pattern of A => Option[B]
 * - once an element gets None'd out, we're still executing `map` and `filter` operations on it
 *   which is needless stack frames and pattern matching
 *
 * Hence this version was made where each stage was stored as an Any => Option[Any] in a simple collection
 *
 * A tight loop iterates through the transformations for a particular element short-circuiting out
 * once a None is encountered.
 *
 * The cost for this is:
 * - a loss of type safety internally
 * - casts at the time of execution to be able to use the functions passed in
 */
class ShortFuse[A, B: ClassTag] private(array: Array[A], transforms: Vector[Any => Option[Any]]) {

  def map[C: ClassTag](f: B => C): ShortFuse[A, C] = {
    // The transform isn't quite in the right shape
    val nextTransform: Any => Option[Any] = (b: Any) => Some(f(b.asInstanceOf[B]))
    new ShortFuse(array, transforms :+ nextTransform)
  }

  def filter(pred: B => Boolean): ShortFuse[A, B] = {
    // The transform isn't quite in the right shape
    val nextTransform: Any => Option[Any] = (b: Any) => {
      // NOTE: Intellij wants me to change this to `Some(b.asInstanceOf[B]).filter(pred)
      // but in the cases where it fails the check, it will needlessly wrap b in a Some
      // and call a `filter` method which is more object allocation and stack frames.
      // This code is meant to be high performance so we're avoiding little bits of overhead like that.
      // The code below is less elegant but probably performs better.
      val bChecked = b.asInstanceOf[B]
      if (pred(bChecked)) Some(bChecked)
      else None
    }
    new ShortFuse(array, transforms :+ nextTransform)
  }

  def boom: Array[B] = {
    val buffer = fillBuffer(array, start = 0, stop = array.length, transformsArray = transforms.toArray)
    buffer.toArray
  }

  def parBoom(implicit ec: C4ExecutionContext): Array[B] = {
    val slices = partitions(array.length, ec.parallelism)
    val transformsArray = transforms.toArray

    val parallelized: Future[Array[B]] = Future.traverse(slices) { case (start, stop) =>
      Future {
        fillBuffer(array, start, stop, transformsArray)
      }(ec.threadPool)
    }.flatMap(a => flattenBuffers(a))

    Await.result(parallelized, Duration.Inf)
  }

  private def fillBuffer(array: Array[A], start: Int, stop: Int, transformsArray: Array[Any => Option[Any]]): ArrayBuffer[B] = {
    val buffer = ArrayBuffer.empty[B]
    val numTransforms = transformsArray.length

    cfor(start)(_ < stop, _ + 1) { i =>
      var current: Option[Any] = Some(array(i))
      cfor(0)(_ < numTransforms && !current.isEmpty, _ + 1) { t =>
        current = transformsArray(t).apply(current.get)
      }

      current.foreach { survivor =>
        // If the element survived to the end of the chain, it's a B
        buffer.append(survivor.asInstanceOf[B])
      }
    }
    buffer
  }

}

object ShortFuse {
  def apply[A: ClassTag](array: Array[A]): ShortFuse[A, A] = new ShortFuse[A, A](array, Vector.empty)

  implicit class Ops[A: ClassTag](array: Array[A]) {
    def shortFuse: ShortFuse[A, A] = ShortFuse(array)
  }
}
