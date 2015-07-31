FROM maven:3.3-jdk-8-onbuild

RUN \
  apt-get update && \
  apt-get install -y python python-dev python-pip python-virtualenv && \
  rm -rf /var/lib/apt/lists/*


RUN pip install brick_wall_build