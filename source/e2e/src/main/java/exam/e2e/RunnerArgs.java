// Owner: Nguoi1

package exam.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tham số dòng lệnh của EndToEndRunner. */
class RunnerArgs {

    final String python;
    final String backend;
    final String javaCommand;
    final Path mlDirectory;
    final String serverClasspath;
    final Path workDirectory;

    RunnerArgs(String[] args) throws IOException {
        String pythonArg = "python3";
        String backendArg = "naive";
        String javaArg = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String mlDirArg = "ml-service";
        String classpathArg = "source/server/target/server.jar:source/server/target/lib/*";
        String workDirArg = null;

        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length || !args[i].startsWith("--")) {
                throw new IllegalArgumentException("Tham số không hợp lệ: " + args[i]);
            }
            String value = args[i + 1];
            switch (args[i]) {
                case "--python":
                    pythonArg = value;
                    break;
                case "--backend":
                    backendArg = value;
                    break;
                case "--java":
                    javaArg = value;
                    break;
                case "--ml-dir":
                    mlDirArg = value;
                    break;
                case "--server-cp":
                    classpathArg = value;
                    break;
                case "--work-dir":
                    workDirArg = value;
                    break;
                default:
                    throw new IllegalArgumentException("Không có tham số " + args[i]);
            }
        }
        if (!backendArg.equals("naive") && !backendArg.equals("chronos")) {
            throw new IllegalArgumentException("--backend phải là naive hoặc chronos");
        }

        this.python = pythonArg;
        this.backend = backendArg;
        this.javaCommand = javaArg;
        this.mlDirectory = Path.of(mlDirArg).toAbsolutePath();
        // Server chạy với thư mục làm việc khác, nên classpath tương đối phải đổi thành tuyệt đối.
        this.serverClasspath = absolutize(classpathArg);
        this.workDirectory = workDirArg != null ? Path.of(workDirArg).toAbsolutePath() : Files.createTempDirectory("ltm-e2e");
    }

    private static String absolutize(String classpath) {
        StringBuilder result = new StringBuilder();
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            if (result.length() > 0) {
                result.append(java.io.File.pathSeparator);
            }
            result.append(Path.of(entry).toAbsolutePath());
        }
        return result.toString();
    }
}
