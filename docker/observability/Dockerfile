FROM fluent/fluent-bit:4.2.3-debug@sha256:6d44cfdd0c37dffd33699fd379c41603a25ce8da2c241a82e30e9e75ed293680
USER root
RUN mkdir -p /collector /data/archive /data/buffer && chown -R 10001:10001 /collector /data
COPY --chown=10001:10001 archive.py fluent-bit.conf /collector/
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
USER 10001:10001
EXPOSE 24224/tcp
ENTRYPOINT ["/usr/bin/python3", "/collector/archive.py"]
