package org.nhlstenden.moniter.cli;

import org.apache.commons.cli.*;

public final class CliOptions {
    private CliOptions() {
    }

    public static CommandLine parse(String[] args) {
        Options options = new Options();

        options.addOption(
                Option.builder("r")
                        .longOpt("repo")
                        .hasArg()
                        .argName("owner/repo")
                        .desc("GitHub repository (owner/repo)")
                        .required()
                        .build()
        );

        options.addOption(
                Option.builder("t")
                        .longOpt("token")
                        .hasArg()
                        .argName("token")
                        .desc("Github Personal Access Token")
                        .required()
                        .build()
        );

        options.addOption(
                Option.builder("i")
                        .longOpt("interval")
                        .hasArg()
                        .argName("seconds")
                        .desc("Polling interval in seconds")
                        .build()
        );

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            return parser.parse(options, args);
        } catch (ParseException e) {
            formatter.printHelp("GitHub Checks", options);
            System.exit(1);
            return null;
        }
    }
}
