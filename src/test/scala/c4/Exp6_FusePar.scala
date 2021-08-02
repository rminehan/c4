package c4

import org.scalameter.api._
import PerformanceUtils._
import c4.C4ParUtils.C4ExecutionContext
import c4.Fuse.Ops

/** Measures the speed up by fusing steps together to avoid intermediate collections
 * as well as parallelizing the internal execution.
 */
class Exp6_FusePar extends Bench.LocalTime {

  // TODO - tune this based on your machine
  implicit val ec: C4ExecutionContext = C4ExecutionContext(parallelism = 4)

  performance of "Fuse" in {

    measure method "map" in {
      using(arrays) in {
        a => a.fuse.map(_ * 2).parBoom
      }
    }

    measure method "map.filter" in {
      using(arrays) in {
        a => a.fuse.map(_ * 2).filter(_ % 6 != 0).parBoom
      }
    }

    measure method "map.filter.map.filter" in {
      using(arrays) in {
        a => a.fuse.map(_ * 2).filter(_ % 6 != 0).map(_ * 3).filter(_ > 300).parBoom
      }
    }

  }
}
