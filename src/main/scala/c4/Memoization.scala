package c4

import c4.C4ParUtils.{C4ExecutionContext, flattenBuffers}
import spire.implicits.cfor

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

/** Extensions for Array which allow for caching of individual computations
 * within a combinator.
 *
 * e.g. Array(person1, person2, person3).filterByKey(_.signupDate)(getsSignupPromotion)
 *
 * where `def getsSignupPromotion(signupDate: LocalDate): Boolean = ...`
 *
 * The closest alternative using standard tools would be:
 *
 * Array(...).filter(person => getsSignupPromotion(person.signupDate))
 *
 * The issues with this approach are:
 *
 * - it's a little harder to see the individual steps
 * - we're recalculating the outputs from getsSignupPromotion when many users would have
 *   signed up on the same date.
 *
 * The `*ByKey` methods allow you to express the part of your data which is being used
 * to drive the filter/map step which gives the library enough insight to cache the meaningful inputs,
 * but it does this without you having to generate an intermediate collection for those values
 *
 * TODO - not sure if this should live in c4 package
 */
object Memoization {
  implicit class Ops[A: ClassTag](array: Array[A]) {
    def mapByKey[K, B: ClassTag](key: A => K)(f: K => B): Array[B] = {
      val mapped = Array.ofDim[B](array.length)
      fillArraySlice(mapped, 0, mapped.length, key, f)
      mapped
    }

    def mapByKeyPar[K, B: ClassTag](key: A => K)(f: K => B)(implicit ec: C4ExecutionContext): Array[B] = {
      val mapped = Array.ofDim[B](array.length)
      val slices = C4ParUtils.partitions(mapped.length, ec.parallelism)

      val parallelized = Future.traverse(slices) { case (start, stop) =>
        Future {
          fillArraySlice(mapped, start, stop, key, f)
        }(ec.threadPool)
      }

      Await.result(parallelized, Duration.Inf)

      mapped
    }

    // Like mapByKeyPar except it uses a lock-striped hash map internally.
    // This reduces the amount of memory needed and increases the cache reuse.
    // There is still a chance of lock contention as some keys will collide on shared locks.
    def mapByKeyConc[K, B: ClassTag](key: A => K)(f: K => B)(implicit ec: C4ExecutionContext): Array[B] = {
      val mapped = Array.ofDim[B](array.length)
      val slices = C4ParUtils.partitions(mapped.length, ec.parallelism)

      val cache = new java.util.concurrent.ConcurrentHashMap[K, B]()

      val parallelized = Future.traverse(slices) { case (start, stop) =>
        Future {
          cfor(start)(_ < stop, _ + 1) { i =>
            val k = key(array(i))
            val b = cache.computeIfAbsent(k, (t: K) => f(t))
            mapped(i) = b
          }
        }(ec.threadPool)
      }

      Await.result(parallelized, Duration.Inf)

      mapped
    }

    private def fillArraySlice[B, K](mapped: Array[B], start: Int, stop: Int, key: A => K, f: K => B): Unit = {
      val cache: mutable.Map[K, B] = mutable.Map.empty[K, B]

      cfor(start)(_ < stop, _ + 1) { i =>
        val k = key(array(i))
        val b = cache.getOrElseUpdate(k, f(k))
        mapped(i) = b
      }
    }

    def filterByKey[K](key: A => K)(pred: K => Boolean): Array[A] = {
      filteredBuffer(array, 0, array.length, key, pred).toArray
    }

    def filterByKeyPar[K](key: A => K)(pred: K => Boolean)(implicit ec: C4ExecutionContext): Array[A] = {
      val slices = C4ParUtils.partitions(array.length, ec.parallelism)

      val parallelized = Future.traverse(slices) { case (start, stop) =>
        Future(filteredBuffer(array, start, stop, key, pred))(ec.threadPool)
      }.flatMap(flattenBuffers)

      Await.result(parallelized, Duration.Inf)
    }

    def filterByKeyConc[K](key: A => K)(pred: K => Boolean)(implicit ec: C4ExecutionContext): Array[A] = {
      val slices = C4ParUtils.partitions(array.length, ec.parallelism)
      val cache = new java.util.concurrent.ConcurrentHashMap[K, Boolean]()

      val parallelized = Future.traverse(slices) { case (start, stop) =>
        Future {
          val buffer = ArrayBuffer.empty[A]
          cfor(start)(_ < stop, _ + 1) { i =>
            val element = array(i)
            val k = key(element)
            val bool = cache.computeIfAbsent(k, (t: K) => pred(t))
            if (bool) buffer.append(element)
          }
          buffer
        }(ec.threadPool)
      }.flatMap(flattenBuffers)

      Await.result(parallelized, Duration.Inf)
    }

    private def filteredBuffer[K](array: Array[A], start: Int, stop: Int, key: A => K, pred: K => Boolean): ArrayBuffer[A] = {
      val buffer = ArrayBuffer.empty[A]

      val cache = mutable.Map.empty[K, Boolean]

      cfor(start)(_ < stop, _ + 1) { i =>
        val element = array(i)
        val k = key(element)
        val bool = cache.getOrElseUpdate(k, pred(k))
        if (bool) buffer.append(element)
      }

      buffer
    }
  }
}
