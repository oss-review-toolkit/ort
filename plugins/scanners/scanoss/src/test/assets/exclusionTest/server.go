package cmd

import (
	_ "embed"
	"flag"
	"fmt"
	"os"
	"strings"

	"github.com/golobby/config/v3"
	"github.com/golobby/config/v3/pkg/feeder"
	zlog "github.com/scanoss/zap-logging-helper/pkg/logger"
	myconfig "scanoss.com/go-api/pkg/config"
	"scanoss.com/go-api/pkg/protocol/rest"
)

//go:generate bash ../../get_version.sh
//go:embed version.txt
var version string

// getConfig checks command line args for option to feed into the config parser.
func getConfig() (*myconfig.ServerConfig, error) {
	var jsonConfig, envConfig, loggingConfig string
	flag.StringVar(&jsonConfig, "json-config", "", "Application JSON config")
	flag.StringVar(&envConfig, "env-config", "", "Application dot-ENV config")
	flag.StringVar(&loggingConfig, "logging-config", "", "Logging config file")
	debug := flag.Bool("debug", false, "Enable debug")
	ver := flag.Bool("version", false, "Display current version")
	flag.Parse()
	if *ver {
		fmt.Printf("Version: %v", version)
		os.Exit(1)
	}
	var feeders []config.Feeder
	if len(jsonConfig) > 0 {
		feeders = append(feeders, feeder.Json{Path: jsonConfig})
	}
	if len(envConfig) > 0 {
		feeders = append(feeders, feeder.DotEnv{Path: envConfig})
	}
	if *debug {
		err := os.Setenv("APP_DEBUG", "1")
		if err != nil {
			fmt.Printf("Warning: Failed to set env APP_DEBUG to 1: %v", err)
			return nil, err
		}
	}
	myConfig, err := myconfig.NewServerConfig(feeders)
	if len(loggingConfig) > 0 {
		myConfig.Logging.ConfigFile = loggingConfig // Override any logging config file with this one.
	}
	return myConfig, err
}

// setupEnvVars configures a custom env vars for the scanoss engine.
func setupEnvVars(cfg *myconfig.ServerConfig) {
	if len(cfg.Scanning.ScanningURL) > 0 {
		err := os.Setenv("SCANOSS_API_URL", cfg.Scanning.ScanningURL)
		if err != nil {
			zlog.S.Infof("Failed to set alternative SCANOSS_API_URL value to %s: %v", cfg.Scanning.ScanningURL, err)
		}
	}
	if customURL := os.Getenv("SCANOSS_API_URL"); len(customURL) > 0 {
		zlog.S.Infof("Using custom API URL: %s", customURL)
	}
	err := os.Setenv("SCANOSS_FILE_CONTENTS", fmt.Sprintf("%v", cfg.Scanning.FileContents))
	if err != nil {
		zlog.S.Infof("Failed to set SCANOSS_FILE_CONTENTS SCANOSS_API_URL value to %v: %v", cfg.Scanning.FileContents, err)
	}
	if customContents := os.Getenv("SCANOSS_FILE_CONTENTS"); len(customContents) > 0 && customContents == "false" {
		zlog.S.Infof("Skipping file_url datafield.")
	}
}

// RunServer runs the gRPC Dependency Server.
func RunServer() error {
	// Load command line options and config
	cfg, err := getConfig()
	if err != nil {
		return fmt.Errorf("failed to load config: %v", err)
	}
	// Check mode to determine which logger to load
	{
		var err error
		switch strings.ToLower(cfg.App.Mode) {
		case "prod":
			if len(cfg.Logging.ConfigFile) > 0 {
				err = zlog.NewSugaredLoggerFromFile(cfg.Logging.ConfigFile)
			} else {
				err = zlog.NewSugaredProdLogger(cfg.Logging.OutputPaths...)
			}
		default:
			if len(cfg.Logging.ConfigFile) > 0 {
				err = zlog.NewSugaredLoggerFromFile(cfg.Logging.ConfigFile)
			} else {
				err = zlog.NewSugaredDevLogger()
			}
		}
		if err != nil {
			return fmt.Errorf("failed to load logger: %v", err)
		}
		if cfg.App.Debug {
			zlog.SetLevel("debug")
		}
		zlog.L.Debug("Running with debug enabled")
		defer zlog.SyncZap()
	}
	zlog.S.Infof("Starting SCANOSS Dependency Service: %v", strings.TrimSpace(version))
	// Setup database connection pool
	if cfg.Logging.DynamicLogging && len(cfg.Logging.DynamicPort) > 0 {
		zlog.S.Infof("Setting up dynamic logging level on %v.", cfg.Logging.DynamicPort)
		zlog.SetupDynamicLogging(cfg.Logging.DynamicPort)
		zlog.S.Infof("Use the following to get the current status: curl -X GET %v/log/level", cfg.Logging.DynamicPort)
		zlog.S.Infof("Use the following to set the current status: curl -X PUT %v/log/level -d level=debug", cfg.Logging.DynamicPort)
	}
	zlog.S.Infof("Running with %v worker(s) per scan request", cfg.Scanning.Workers)
	zlog.S.Infof("Running with config: %+v", *cfg)
	// Setup custom env variables if requested
	setupEnvVars(cfg)
	return rest.RunServer(cfg, version)
}

