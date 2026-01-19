defmodule ChildApp.MixProject do
  use Mix.Project

  def project do
    [
      app: :child_app,
      version: "0.0.0-dev",
      build_path: "../../_build",
      config_path: "../../config/config.exs",
      deps_path: "../../deps",
      lockfile: "../../mix.lock",
      deps: [
        {:mime, "~> 2.0"}
      ]
    ]
  end
end
