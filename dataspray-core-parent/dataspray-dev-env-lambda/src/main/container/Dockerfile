# TODO set as ...-arm64
FROM public.ecr.aws/lambda/python:3.9
ARG PROJECT_VERSION

CMD [ "dataspray.main.handler" ]

RUN python3 -m pip install --upgrade pip

# Development environment tooling
# TODO re-enable
# RUN yum install -y java-17-amazon-corretto-headless maven

# Dependencies
COPY requirements.txt ${LAMBDA_TASK_ROOT}
RUN  pip3 install -r requirements.txt --target "${LAMBDA_TASK_ROOT}"

# Server code
COPY dataspray-${PROJECT_VERSION}-py3-none-any.whl ${LAMBDA_TASK_ROOT}
RUN  pip3 install dataspray-${PROJECT_VERSION}-py3-none-any.whl --target ${LAMBDA_TASK_ROOT}
