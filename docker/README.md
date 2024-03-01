# Usage

To build the `Dockerfile` in the root with plain Docker, use

    docker build -t ort .

To verify that the built image includes all required tools, use

    docker run -e FORCE_COLOR=$COLORTERM ort requirements --list=COMMANDS
