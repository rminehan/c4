package c4

import org.scalameter.api._
import PerformanceUtils._
import C4ArrayPar.{C4ExecutionContext, Ops}

/** Measures the speed up by reimplementing map and filter using a parallelized cfor based implementation.
 * The main speed up is from parallelization by mapping and filtering big slices of the array.
 * Within those slices there is cfor loops which add a bit of extra value.
 */
class Exp4_C4Par extends Bench.LocalTime {

  // TODO - tune this based on your machine
  implicit val ec: C4ExecutionContext = C4ExecutionContext(parallelism = 4)

  performance of "C4ArrayPar.Ops" in {

    measure method "map" in {
      using(arrays) in {
        a => a.mapC4Par(_ * 2)
      }
    }

    measure method "map.filter" in {
      using(arrays) in {
        a => a.mapC4Par(_ * 2).filterC4Par(_ % 6 != 0)
      }
    }

    measure method "map.filter.map.filter" in {
      using(arrays) in {
        a => a.mapC4Par(_ * 2).filterC4Par(_ % 6 != 0).mapC4Par(_ * 3).filterC4Par(_ > 300)
      }
    }

    // Heavy factorial test left out to avoid burning a hole in my laptop
    // and draining all the battery

  }
}
