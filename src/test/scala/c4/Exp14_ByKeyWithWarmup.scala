package c4

import org.scalameter.api._
import PerformanceUtils._
import c4.C4ParUtils.C4ExecutionContext
import c4.Memoization.Ops

// Really a continuation of Exp11 but by the time I'd thought of it
// I'd already created all the slides so it was too much work to
// insert it between Exp11 and Exp12
class Exp14_ByKeyWithWarmup extends Bench.LocalTime {

  // TODO - tune this based on your machine
  implicit val ec: C4ExecutionContext = C4ExecutionContext(parallelism = 4)

  // Our data has 100 unique values uniformly distributed
  // and then gradually gets less unique as values are mapped and filtered
  // deterministically.
  // After processing 500 values,
  // the odds of a value not having hit the cache is pretty small,
  // (you could work it out using the binomial distribution I think)
  // and 500 is a tiny amount of iteration compared to the sizes of the arrays
  // being processed (250K+)
  val warmup = 500

  performance of "*ByKeyWithWarmup" in {

    measure method "map" in {
      using(arrays) in {
        a => a.mapByKeyWithWarmup(warmup)(i => i)(_ * 2)
      }
    }

    measure method "map.filter" in {
      using(arrays) in {
        a => a
          .mapByKeyWithWarmup(warmup)(i => i)(_ * 2)
          .filterByKeyWithWarmup(warmup)(i => i)(_ % 6 != 0)
      }
    }

    measure method "map.filter.map.filter" in {
      using(arrays) in {
        a => a
          .mapByKeyWithWarmup(warmup)(i => i)(_ * 2)
          .filterByKeyWithWarmup(warmup)(i => i)(_ % 6 != 0)
          .mapByKeyWithWarmup(warmup)(i => i)(_ * 3)
          .filterByKeyWithWarmup(warmup)(i => i)(_ > 300)
      }
    }

    measure method "Heavy work: map(fac)" in {
      using(arrays) in {
        a => a.mapByKeyWithWarmup(warmup)(i => i)(fac)
      }
    }

  }
}
