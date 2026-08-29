defmodule UmbrellaName.MixProject do
  use Mix.Project

  def project do
    [
      apps_path: "apps",
      version: "0.0.0-dev",
      deps: [
        {:credo, "~> 1.7"}
      ]
    ]
  end
end
