from pathlib import Path

from app.services.gpu_status import _parse_ollama_server_log


def test_parse_ollama_server_log_detects_intel_arc(tmp_path: Path):
    log = tmp_path / "server.log"
    log.write_text(
        'time=2026-06-06 level=INFO source=types.go:32 msg="inference compute" '
        'id=0 library=Vulkan name=Vulkan0 description="Intel(R) Arc(TM) A770 Graphics" '
        'total="15.9 GiB" available="15.1 GiB"\n',
        encoding="utf-8",
    )
    status = _parse_ollama_server_log(log)
    assert status.gpu_available is True
    assert status.gpu_device == "Intel(R) Arc(TM) A770 Graphics"
    assert status.gpu_library == "Vulkan"
    assert status.gpu_vram_total == "15.9 GiB"


def test_processor_uses_gpu():
    from app.services.gpu_status import _processor_uses_gpu

    assert _processor_uses_gpu("100% GPU", None) is True
    assert _processor_uses_gpu("100% CPU", None) is False
    assert _processor_uses_gpu("48%/52% CPU/GPU", None) is True
    assert _processor_uses_gpu(None, 1024) is True
