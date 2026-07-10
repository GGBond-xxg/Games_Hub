cmake_minimum_required(VERSION 3.22.1)
project(md3e_gba_cheat)

add_library(md3e_gba_cheat SHARED gba_cheat_bridge.cpp)

target_compile_features(md3e_gba_cheat PRIVATE cxx_std_17)

target_link_libraries(md3e_gba_cheat android log dl)
