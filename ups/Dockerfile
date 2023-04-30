FROM python:3
ENV PYTHONUNBUFFERED 1
RUN mkdir /code
RUN chmod 777 /code
WORKDIR /code
COPY requirements.txt /code/
RUN pip install -r requirements.txt
COPY initserver.sh /code/initserver.sh
COPY runserver.sh /code/runserver.sh
COPY startserver.sh /code/startserver.sh
# Set permissions for the shell scripts
RUN chmod 777 /code/initserver.sh
RUN chmod 777 /code/runserver.sh
RUN chmod 777 /code/startserver.sh
COPY . /code/


