defmodule Sample1.MixProject do
  use Mix.Project

  def project do
    [
      app: :sample1,
      version: "0.1.0",
      elixir: "~> 1.8",
      start_permanent: Mix.env() == :prod,
      deps: deps(),
      package: [
        links: %{
          Github: "https://github.com/sample1",
          Changelog: "https://hexdocs.pm/sample1/changelog.html"
        }
      ]
    ]
  end

  # Run "mix help compile.app" to learn about applications.
  def application do
    [
      extra_applications: [:logger]
    ]
  end

  # Run "mix help deps" to learn about dependencies.
  defp deps do
    [
      {:hackney, "~> 1.15"},
      {:sweet_xml, "~> 0.6.6"},
      {:jason, "~> 1.1", optional: true},
      {:ex_doc, "~> 0.21.2", only: :dev},
      {:meck, "~> 0.8.13", only: :test}
    ]
  end
end
