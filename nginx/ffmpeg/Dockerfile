FROM python:3.12-alpine

RUN apk add --no-cache ffmpeg && \
    pip install --no-cache-dir flask==3.1.0 gunicorn==23.0.0

WORKDIR /app
COPY server.py /app/server.py

EXPOSE 8099

CMD ["gunicorn","-b","0.0.0.0:8099","server:app","--workers","1","--threads","1","--timeout","3600"]

