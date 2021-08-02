package c4

import org.scalameter.api.Gen
import spire.syntax.literals.si._
import spire.implicits.cfor

import scala.annotation.tailrec

object PerformanceUtils {

  val rng = new scala.util.Random(155)

  val arrayLengths: Gen[Int] = Gen.range("size")(
    from = i"250 000",
    upto = i"1 000 000",
    hop = i"250 000"
  )

  val arrays: Gen[Array[Int]] = for {
    length <- arrayLengths
  } yield {

    val array = Array.ofDim[Int](length)
    cfor(0)(_ < length, _ + 1) { i =>
      array(i) = rng.nextInt(100)
    }
    array

  }

  def fac(n: Int): BigInt = {
    @tailrec
    def facTail(acc: BigInt, n: Int): BigInt = n match {
      case 0 => acc
      case _ => facTail(acc * n, n - 1)
    }
    facTail(BigInt(1), n)
  }

}
