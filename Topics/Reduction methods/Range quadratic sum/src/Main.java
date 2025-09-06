import java.util.stream.*;

class QuadraticSum {
    public static long rangeQuadraticSum(int fromIncl, int toExcl) {
        return IntStream.range(fromIncl, toExcl).reduce(0, (int accumulator, int next) -> accumulator + (next * next) );
    }
}