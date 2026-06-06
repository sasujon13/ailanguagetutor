from app.services.cache_l1 import CacheManager, cache_key


def test_cache_l1_hit():
    cache = CacheManager(l1_ttl_sec=60.0)
    key = cache_key("hello", "answer", 1, "en", [])
    cache.set(key, {"explanation": "test"})
    assert cache.get(key) == {"explanation": "test"}
    stats = cache.stats()
    assert stats["l1_hits"] == 1
    assert stats["misses"] == 0


def test_cache_l2_promotion():
    cache = CacheManager(l1_ttl_sec=0.01, l1_max=1)
    key = cache_key("promote me", "translation", 2, "en", ["fr"])
    cache.l2.set(key, {"fr": "bonjour"})
    hit = cache.get(key)
    assert hit == {"fr": "bonjour"}
    assert cache.l2_hits == 1
    assert cache.get(key) == {"fr": "bonjour"}
    assert cache.l1_hits >= 1


def test_cache_l3_promotion():
    cache = CacheManager(l1_ttl_sec=0.01)
    key = cache_key("persist", "answer", 1, "en", [])
    cache.l3.set(key, {"explanation": "stored"})
    hit = cache.get(key)
    assert hit == {"explanation": "stored"}
    assert cache.l3_hits == 1


def test_cache_miss_increments():
    cache = CacheManager()
    assert cache.get("nonexistent-key") is None
    assert cache.misses == 1
