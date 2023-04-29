FROM python:3
ENV PYTHONUNBUFFERED 1
RUN mkdir /code
RUN chmod 777 /code
WORKDIR /code
ADD requirements.txt /code/
RUN pip install -r requirements.txt
ADD . /code/
RUN chmod 777 initserver.sh
RUN chmod 777 runserver.sh
RUN chmod 777 startserver.sh
