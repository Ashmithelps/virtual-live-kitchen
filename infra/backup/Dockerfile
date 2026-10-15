# pg_dump/pg_restore + AWS CLI (for R2) + age (optional encryption). Keep the Postgres major version equal to
# (or newer than) the managed database's: pg_dump refuses to dump a newer server.
ARG PG_IMAGE=postgres:16.15-alpine3.24
FROM ${PG_IMAGE}
RUN apk add --no-cache aws-cli age bash coreutils
COPY backup.sh restore.sh schedule.sh common.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/schedule.sh \
    && ln -s /usr/local/bin/backup.sh /usr/local/bin/backup-now \
    && ln -s /usr/local/bin/schedule.sh /usr/local/bin/schedule \
    && ln -s /usr/local/bin/restore.sh /usr/local/bin/restore
USER postgres
WORKDIR /tmp
ENTRYPOINT []
CMD ["schedule"]
