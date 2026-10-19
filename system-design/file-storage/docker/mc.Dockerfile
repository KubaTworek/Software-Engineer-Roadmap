# Klient restore drill z oficjalnych źródeł RELEASE.2025-04-16T18-13-26Z.
FROM golang:1.24.2-bookworm AS build
WORKDIR /src
RUN git init . \
    && git remote add origin https://github.com/minio/mc.git \
    && git fetch --depth=1 origin b00526b153a31b36767991a4f5ce2cced435ee8e \
    && git checkout --detach FETCH_HEAD \
    && test "$(git rev-parse HEAD)" = "b00526b153a31b36767991a4f5ce2cced435ee8e"
RUN CGO_ENABLED=0 go build -mod=readonly -p=2 -trimpath -buildvcs=false -o /out/mc .

FROM alpine:3.21.3
COPY --from=build /out/mc /usr/local/bin/mc
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=build /src/LICENSE /licenses/mc/LICENSE
ENTRYPOINT ["mc"]
