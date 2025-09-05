import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        // write your code here

        Scanner scanner = new Scanner(System.in);

        String string = scanner.nextLine();

        Arrays.stream(
                string.split("\\s"))
                .map(String::toLowerCase)
                .map(w -> w.replaceAll("[^A-Za-z0-9]", ""))
                .collect(
                        Collectors.groupingBy(
                                w -> w,
                                Collectors.counting()
                        )
                )
                .entrySet().stream()
                .sorted(
                        Map.Entry.<String, Long>comparingByValue().reversed()
                                .thenComparing(
                                        Map.Entry.<String, Long>comparingByKey()
                                )
                )
                .map(Map.Entry::getKey)
                .limit(10)
                .forEach(System.out::println);
    }
}