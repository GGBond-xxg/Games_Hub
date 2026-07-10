#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <unistd.h>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>
#include <algorithm>

#define LOG_TAG "MD3E_GBA_NATIVE"
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

using RetroGetMemoryData = void* (*)(unsigned);
using RetroGetMemorySize = size_t (*)(unsigned);
using RetroCheatReset = void (*)();
using RetroCheatSet = void (*)(unsigned, bool, const char*);

struct MemorySnapshot {
    unsigned id = 0;
    void* ptr = nullptr;
    size_t size = 0;
    std::vector<uint8_t> bytes;
};

struct RomMirror {
    uint8_t* base = nullptr;
    size_t size = 0;
    std::vector<uint8_t> original;
};

void* gCoreHandle = nullptr;
RetroGetMemoryData gGetMemoryData = nullptr;
RetroGetMemorySize gGetMemorySize = nullptr;
RetroCheatReset gCheatReset = nullptr;
RetroCheatSet gCheatSet = nullptr;
std::string gCorePath;
std::string gRomPath;
std::vector<uint8_t> gRomBytes;
std::vector<MemorySnapshot> gSnapshots;
std::vector<RomMirror> gRomMirrors;

std::string ok(const std::string& msg) { return "OK|" + msg; }
std::string err(const std::string& msg) { return "ERR|" + msg; }

std::string jstr(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return out;
}

jstring ret(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

bool readFile(const std::string& path, std::vector<uint8_t>& out) {
    std::ifstream in(path, std::ios::binary);
    if (!in) return false;
    in.seekg(0, std::ios::end);
    auto len = in.tellg();
    if (len <= 0) return false;
    if (len > 64 * 1024 * 1024) return false;
    in.seekg(0, std::ios::beg);
    out.resize(static_cast<size_t>(len));
    in.read(reinterpret_cast<char*>(out.data()), static_cast<std::streamsize>(out.size()));
    return static_cast<size_t>(in.gcount()) == out.size();
}

bool resolveCore() {
    if (gCoreHandle && gGetMemoryData && gGetMemorySize) return true;
    if (gCorePath.empty()) return false;

#ifdef RTLD_NOLOAD
    gCoreHandle = dlopen(gCorePath.c_str(), RTLD_NOW | RTLD_NOLOAD);
#endif
    if (!gCoreHandle) {
        // If the handle was not opened globally, dlopen with the same path should return the
        // already-loaded object or map it with the same soname. We only use public libretro symbols.
        gCoreHandle = dlopen(gCorePath.c_str(), RTLD_NOW);
    }
    if (!gCoreHandle) {
        ALOGW("dlopen core failed: %s", dlerror());
        return false;
    }

    gGetMemoryData = reinterpret_cast<RetroGetMemoryData>(dlsym(gCoreHandle, "retro_get_memory_data"));
    gGetMemorySize = reinterpret_cast<RetroGetMemorySize>(dlsym(gCoreHandle, "retro_get_memory_size"));
    gCheatReset = reinterpret_cast<RetroCheatReset>(dlsym(gCoreHandle, "retro_cheat_reset"));
    gCheatSet = reinterpret_cast<RetroCheatSet>(dlsym(gCoreHandle, "retro_cheat_set"));

    ALOGD("resolveCore memoryData=%p memorySize=%p cheatReset=%p cheatSet=%p", gGetMemoryData, gGetMemorySize, gCheatReset, gCheatSet);
    return gGetMemoryData && gGetMemorySize;
}

bool overlaps(const uint8_t* a, size_t as, const uint8_t* b, size_t bs) {
    auto ae = a + as;
    auto be = b + bs;
    return a < be && b < ae;
}

bool alreadyMirror(uint8_t* base, size_t size) {
    for (const auto& m : gRomMirrors) {
        if (m.base == base && m.size == size) return true;
    }
    return false;
}

void findRomMirrors() {
    gRomMirrors.clear();
    if (gRomBytes.size() < 0xC0) return;

    constexpr size_t patternOffset = 0x04;
    constexpr size_t patternSize = 0x9C; // Nintendo logo area, usually stable and highly unique.
    const uint8_t* pattern = gRomBytes.data() + patternOffset;
    const uint8_t* ownStart = gRomBytes.data();
    const size_t ownSize = gRomBytes.size();

    std::ifstream maps("/proc/self/maps");
    std::string line;
    while (std::getline(maps, line)) {
        if (gRomMirrors.size() >= 4) break;
        unsigned long startLong = 0, endLong = 0;
        char perms[5] = {0};
        if (std::sscanf(line.c_str(), "%lx-%lx %4s", &startLong, &endLong, perms) != 3) continue;
        uintptr_t start = static_cast<uintptr_t>(startLong);
        uintptr_t end = static_cast<uintptr_t>(endLong);
        if (end <= start) continue;
        const size_t regionSize = static_cast<size_t>(end - start);
        if (regionSize < patternSize + patternOffset) continue;
        if (regionSize > 256ULL * 1024ULL * 1024ULL) continue;
        if (perms[0] != 'r' || perms[1] != 'w') continue; // restore requires writable memory.
        if (line.find("libmd3e_gba_cheat") != std::string::npos) continue;

        auto* region = reinterpret_cast<const uint8_t*>(start);
        const size_t maxPos = regionSize - patternSize;
        for (size_t pos = 0; pos <= maxPos; ++pos) {
            const uint8_t* hit = region + pos;
            if (std::memcmp(hit, pattern, patternSize) != 0) continue;
            if (pos < patternOffset) continue;
            auto* base = const_cast<uint8_t*>(hit - patternOffset);
            if (base < reinterpret_cast<uint8_t*>(start)) continue;
            if (static_cast<size_t>(reinterpret_cast<uintptr_t>(base) + gRomBytes.size() - start) > regionSize) continue;
            if (overlaps(base, gRomBytes.size(), ownStart, ownSize)) continue;
            if (alreadyMirror(base, gRomBytes.size())) continue;

            RomMirror mirror;
            mirror.base = base;
            mirror.size = gRomBytes.size();
            mirror.original = gRomBytes;
            gRomMirrors.push_back(std::move(mirror));
            ALOGD("found ROM mirror base=%p size=%zu line=%s", base, gRomBytes.size(), line.c_str());
            break;
        }
    }
}

std::string captureBaselineImpl() {
    if (!resolveCore()) return err("core symbols unavailable");
    gSnapshots.clear();

    // Libretro standard memory ids. We intentionally avoid SAVE_RAM/RTC.
    // 2 = system RAM, 3 = video RAM. If mGBA exposes them, restoring these can clear RAM-write cheats.
    const unsigned ids[] = {2, 3};
    size_t snapBytes = 0;
    for (unsigned id : ids) {
        void* ptr = gGetMemoryData(id);
        size_t size = gGetMemorySize(id);
        if (!ptr || size == 0 || size > 64ULL * 1024ULL * 1024ULL) {
            ALOGD("skip memory id=%u ptr=%p size=%zu", id, ptr, size);
            continue;
        }
        MemorySnapshot snap;
        snap.id = id;
        snap.ptr = ptr;
        snap.size = size;
        snap.bytes.resize(size);
        std::memcpy(snap.bytes.data(), ptr, size);
        snapBytes += size;
        gSnapshots.push_back(std::move(snap));
        ALOGD("captured memory id=%u ptr=%p size=%zu", id, ptr, size);
    }

    findRomMirrors();
    size_t romBytes = 0;
    for (const auto& mirror : gRomMirrors) romBytes += mirror.size;

    std::ostringstream os;
    os << "baseline snapshots=" << gSnapshots.size()
       << " snapshotBytes=" << snapBytes
       << " romMirrors=" << gRomMirrors.size()
       << " romBytes=" << romBytes;
    return ok(os.str());
}

std::string restoreBaselineImpl() {
    if (!resolveCore()) return err("core symbols unavailable");
    if (gSnapshots.empty() && gRomMirrors.empty()) return err("baseline empty");

    if (gCheatReset) {
        gCheatReset();
        ALOGD("retro_cheat_reset called");
    }

    size_t restoredRegions = 0;
    size_t restoredBytes = 0;
    for (const auto& snap : gSnapshots) {
        if (!snap.ptr || snap.size == 0 || snap.bytes.size() != snap.size) continue;
        std::memcpy(snap.ptr, snap.bytes.data(), snap.size);
        ++restoredRegions;
        restoredBytes += snap.size;
    }

    size_t romRestored = 0;
    size_t romRestoredBytes = 0;
    for (const auto& mirror : gRomMirrors) {
        if (!mirror.base || mirror.size == 0 || mirror.original.size() != mirror.size) continue;
        std::memcpy(mirror.base, mirror.original.data(), mirror.size);
        __builtin___clear_cache(reinterpret_cast<char*>(mirror.base), reinterpret_cast<char*>(mirror.base + mirror.size));
        ++romRestored;
        romRestoredBytes += mirror.size;
    }

    std::ostringstream os;
    os << "restoredRegions=" << restoredRegions
       << " restoredBytes=" << restoredBytes
       << " romMirrors=" << romRestored
       << " romRestoredBytes=" << romRestoredBytes;
    return (restoredRegions > 0 || romRestored > 0) ? ok(os.str()) : err(os.str());
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_bond_md3elauncher_GbaNativeCheatBridge_nativeIsAvailable(JNIEnv*, jclass) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_bond_md3elauncher_GbaNativeCheatBridge_nativeBeginSession(JNIEnv* env, jclass, jstring corePath, jstring romPath) {
    gCorePath = jstr(env, corePath);
    gRomPath = jstr(env, romPath);
    gRomBytes.clear();
    gSnapshots.clear();
    gRomMirrors.clear();

    const bool romOk = readFile(gRomPath, gRomBytes);
    const bool coreOk = resolveCore();
    std::ostringstream os;
    os << "beginSession coreSymbols=" << (coreOk ? 1 : 0)
       << " romLoaded=" << (romOk ? 1 : 0)
       << " romBytes=" << gRomBytes.size();
    return ret(env, (coreOk && romOk) ? ok(os.str()) : err(os.str()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_bond_md3elauncher_GbaNativeCheatBridge_nativeCaptureBaseline(JNIEnv* env, jclass) {
    return ret(env, captureBaselineImpl());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_bond_md3elauncher_GbaNativeCheatBridge_nativeRestoreBaseline(JNIEnv* env, jclass) {
    return ret(env, restoreBaselineImpl());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_bond_md3elauncher_GbaNativeCheatBridge_nativeApplyCheat(JNIEnv* env, jclass, jstring, jstring code) {
    if (!resolveCore()) return ret(env, err("core symbols unavailable"));
    if (!gCheatSet) return ret(env, err("retro_cheat_set unavailable"));
    std::string value = jstr(env, code);
    if (gCheatReset) gCheatReset();
    if (!value.empty()) {
        gCheatSet(0, true, value.c_str());
    }
    std::ostringstream os;
    os << "nativeApply length=" << value.size();
    return ret(env, ok(os.str()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_bond_md3elauncher_GbaNativeCheatBridge_nativeDisableCheat(JNIEnv* env, jclass, jstring) {
    return ret(env, restoreBaselineImpl());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_bond_md3elauncher_GbaNativeCheatBridge_nativeClearAllCheats(JNIEnv* env, jclass) {
    return ret(env, restoreBaselineImpl());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_bond_md3elauncher_GbaNativeCheatBridge_nativeDebugStatus(JNIEnv* env, jclass) {
    std::ostringstream os;
    os << "library=1"
       << " coreHandle=" << (gCoreHandle ? 1 : 0)
       << " memoryData=" << (gGetMemoryData ? 1 : 0)
       << " memorySize=" << (gGetMemorySize ? 1 : 0)
       << " cheatReset=" << (gCheatReset ? 1 : 0)
       << " cheatSet=" << (gCheatSet ? 1 : 0)
       << " romBytes=" << gRomBytes.size()
       << " snapshots=" << gSnapshots.size()
       << " romMirrors=" << gRomMirrors.size();
    return ret(env, ok(os.str()));
}
