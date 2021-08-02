package c4

import org.scalameter.api._
import PerformanceUtils._
import c4.Fuse.Ops

/** Measures the speed up by fusing steps together to avoid intermediate collections.
 * See experiment 6 for the addition of parallelism.
 */
class Exp5_Fuse extends Bench.LocalTime {

  performance of "Fuse" in {

    measure method "map" in {
      using(arrays) in {
        a => a.fuse.map(_ * 2).boom
      }
    }

    measure method "map.filter" in {
      using(arrays) in {
        a => a.fuse.map(_ * 2).filter(_ % 6 != 0).boom
      }
    }

    measure method "map.filter.map.filter" in {
      using(arrays) in {
        a => a.fuse.map(_ * 2).filter(_ % 6 != 0).map(_ * 3).filter(_ > 300).boom
      }
    }

  }
}
