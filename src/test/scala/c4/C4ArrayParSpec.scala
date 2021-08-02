package c4

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class C4ArrayParSpec extends AnyWordSpec with Matchers {
  "partitions" should {
    "correctly partition a simple space" in {
      val actual = C4ArrayPar.partitions(arrayLength = 11, numPartitions = 3)
      actual mustEqual List((0, 4), (4, 8), (8, 11))
    }

    "correctly partition when the number of partitions is greater than the space" in {
      val actual = C4ArrayPar.partitions(arrayLength = 3, numPartitions = 5)
      actual mustEqual List((0, 1), (1, 2), (2, 3), (3, 3), (3, 3))
    }
  }
}