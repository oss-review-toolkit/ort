# Tekton Pipeline Example

This example pipeline allows you to run an ORT on [Tekton](https://tekton.dev/). Some knowledge about Tekton and Kubernetes will help to understand this example pipeline. This example pipeline works best on [Google Kubernetes Engine](https://cloud.google.com/kubernetes-engine) (GKE), since it allows to use Workload Identity to authenticate against [Google Cloud Storage](https://cloud.google.com/products/storage) (GCS) instead of exporting and uploading Service Account Keys.

## Getting Started

To run the pipeline, you will need the following things:
* A Kubernetes cluster with Tekton installed (follow the [installation instructions](https://tekton.dev/docs/pipelines/install/)).
* You will need to install the [git-clone](https://hub.tekton.dev/tekton/task/git-clone) and [gcs-upload](https://hub.tekton.dev/tekton/task/gcs-upload) tasks.
* A [Google Cloud Storage](https://cloud.google.com/products/storage) bucket.

The pipeline is defined in `ort-pipeline.yaml`. You will need to replace `ort:latest` with the full image name, e.g. `gcr.io/<project_id>/ort:latest`.

The pipeline takes a few inputs:
* `shared-data` is a workspace and will need a volume, it's used for checking out the source code repo, and storing the temporary artifacts
* `gcs-credentials` is a workspace and will need a volume. On GKE you can use Workload Identities to allow the Kubernetes Service Account (KSA) to access the GCS bucket for uploading the results. (If you are not using Workload Idenity, find more information how to configure credentials in the [gcs-upload](https://hub.tekton.dev/tekton/task/gcs-upload) documentation).
* `repo-url` is a param, and takes the path to the Git repo that should be scanned.
* `result-bucket-path` is a parameter, and takes the path to GCS where the result report should be stored e.g. gs://<gcs-bucket>/<sub_path>.

In order to get started call `kubectl apply -f ort-pipeline.yaml` to create the pipeline on your Kubernetes cluster. Now, if you want to trigger a pipeline run, you need to create a `PipelineRun` resource. In order to trigger a run, for example for the [Simple Maven example](https://github.com/MarcelBochtler/maven-simple) you can call `kubectl apply -f simple-maven.yaml` (or `kubectl apply -f mime-types.yaml` for the [Mime Types repo](https://github.com/jshttp/mime-types)).

![Example run of Simple Maven](images/simple-maven.png)

![Example run of Mime Types](images/mime-types.png)

## Next Steps
You probably don't want to trigger each execution via `kubectl`. Check out the [Tekton documentation](https://tekton.dev/docs/triggers/) to learn how you can trigger a _PipelineRun_ via _EventListeneres_.

If you aren't working on a public GitHub repository you should also checkout how to work with a [private repo](https://hub.tekton.dev/tekton/task/git-clone).