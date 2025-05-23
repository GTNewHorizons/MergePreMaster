package MergePreMaster;

import lombok.extern.log4j.Log4j2;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;


@Log4j2(topic = "GTNH-Repo-Pre")
@Command(name = "GTNH-Repo-Pre", version = "$VERSION", mixinStandardHelpOptions = true)
public class Main implements Callable<Integer> {

    @Option(names = {"--dryrun"}, defaultValue = "false", description = "Don't execute commands")
    private boolean dryRun;

    @Option(names = {"--fresh"}, description = "Using the existing target branch instead of creating a new one")
    private boolean fresh;

    @Option(names = {"-r", "--remote"}, defaultValue = "origin", description = "Remote to use")
    private String remote = "origin";

    @Option(names = {"-b", "--base"}, defaultValue = "master", description = "Base branch")
    private String baseBranch = "master";

    @Option(names = {"-t", "--target"}, defaultValue = "dev", description = "Target branch")
    private String targetBranch = "dev";

    @Parameters(paramLabel = "<prNum>", description = "PRs to be merged into the target branch")
    private TreeSet<Integer> prNumbers;

    @Override
    public Integer call() throws Exception {
        try {
            runCommand("git", "checkout", baseBranch);
            runCommand("git", "fetch", remote, baseBranch);

            if (fresh || runCommand("git", "rev-parse", "--verify", targetBranch) != 0) {
                runCommand("git", "reset", "--hard", remote + "/" + baseBranch);
                runCommand("git", "checkout", "-B", targetBranch);
            } else {
                runCommand("git", "checkout", targetBranch);
            }

            if (!isAncestor(baseBranch, targetBranch) && runCommand("git", "merge", baseBranch) != 0){
                log.fatal("Error merging {} into {}", baseBranch, targetBranch);
            }

            var failedPRs = new TreeMap<Integer, String>();
            var mergedPRs = new TreeMap<Integer, String>();
            for (int prNumber : prNumbers.reversed()) {
                String prBranch = "pr-" + prNumber;
                log.info("Merging PR #{}", prNumber);
                runCommand("git", "fetch", remote, String.format("pull/%d/head:%s", prNumber, prBranch));
                try {
                    if (isAncestor(prBranch, targetBranch)) {
                        log.info("PR #{} already merged", prNumber);
                        mergedPRs.put(prNumber, "Already Merged");
                        continue;
                    }

                    if (runCommand("git", "merge", "--no-commit", "--no-ff", prBranch) != 0) {
                        throw new RuntimeException("Failed to merge");
                    }

                    log.info("Testing ./gradlew build");

                    if (runCommand(false, "./gradlew", "build") != 0) {
                        throw new RuntimeException("Build failed");
                    }
                    runCommand("git", "commit", "-m", String.format("Merge %s", prBranch));

                    log.info("Merged PR #{}", prNumber);
                    mergedPRs.put(prNumber, "Merged");
                } catch (RuntimeException e) {
                    runCommand("git", "merge", "--abort");
                    failedPRs.put(prNumber, e.getMessage());
                    log.error("Merged failed PR #{}", prNumber);
                }
            }

            if (!failedPRs.isEmpty()) {
                log.warn("The following PRs failed");
                failedPRs.forEach((k, v) -> log.warn("\tPR #{}: {}", k, v));
            }

            if (!prNumbers.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Pre Release Build\n");
                sb.append("Merged PRs (no # means no changes):\n");
                for (var prNum : mergedPRs.entrySet()) {
                    if (prNum.getValue().equals("Merged")) {
                        sb.append(String.format("\t#%d\n", prNum.getKey()));
                    } else {
                        sb.append(String.format("\t%d\n", prNum.getKey()));
                    }
                }
                sb.append("\n\n");
                sb.append("Failed PRs:\n");
                for (var entry : failedPRs.entrySet()) {
                    sb.append(String.format("\t#%d: %s\n", entry.getKey(), entry.getValue()));
                }
                runCommand("git", "commit", "--allow-empty", "-m", sb.toString());
            }
            return 0;
        } catch (Exception e) {
            log.fatal("Unknown error", e);
            return 1;
        }
    }

    private int runCommand(String... commands) throws IOException {
        return runCommand(true, commands);
    }

    private int runCommand(boolean bindIO, String... command) throws IOException {
        if (dryRun) {
            log.info("[Dry Run] Executing '{}'", String.join(" ", command));
            return 0;
        } else {
            log.info("Executing '{}'", String.join(" ", command));
        }
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (bindIO) {
            processBuilder.inheritIO();
        }
        Process process = processBuilder.start();
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return exitCode;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for command to complete", e);
        }
        return 0;
    }

    private boolean isAncestor(String prRef, String targetRef) throws IOException, InterruptedException {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "merge-base", "--is-ancestor", prRef, targetRef);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

}
