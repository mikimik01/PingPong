kotlinc Main.kt -include-runtime -d Main.jar

java -jar Main.jar 127.0.0.1 1234 1235 1

java -jar Main.jar 127.0.0.1 1235 1234
