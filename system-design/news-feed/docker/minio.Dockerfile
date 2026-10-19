# Obraz wyłącznie do laboratoriów; źródła z oficjalnego repozytorium MinIO.
# Przypięty commit wydania RELEASE.2025-04-22T22-12-26Z, bez zależności od minio/minio.
FROM golang:1.24.2-bookworm AS build
WORKDIR /src
RUN git init . \
    && git remote add origin https://github.com/minio/minio.git \
    && git fetch --depth=1 origin 0d7408fc9969caf07de6a8c3a84f9fbb10a6739e \
    && git checkout --detach FETCH_HEAD \
    && test "$(git rev-parse HEAD)" = "0d7408fc9969caf07de6a8c3a84f9fbb10a6739e"
# Ograniczona równoległość kompilacji zapobiega presji pamięci na runnerze CI.
RUN CGO_ENABLED=0 go build -mod=readonly -p=2 -trimpath -buildvcs=false -o /out/minio .

FROM alpine:3.21.3
COPY --from=build /out/minio /usr/local/bin/minio
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=build /src/LICENSE /licenses/minio/LICENSE
RUN mkdir -p /data && chown 10001:10001 /data
USER 10001:10001
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
CMD ["server", "/data"]
