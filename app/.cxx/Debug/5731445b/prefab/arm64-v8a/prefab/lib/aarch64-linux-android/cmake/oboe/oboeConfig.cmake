if(NOT TARGET oboe::oboe)
add_library(oboe::oboe SHARED IMPORTED)
set_target_properties(oboe::oboe PROPERTIES
    IMPORTED_LOCATION "C:/Users/Administrator/.gradle/caches/8.9/transforms/57d1efb4069e1c11f03fe5f35f5a9859/transformed/oboe-1.7.0/prefab/modules/oboe/libs/android.arm64-v8a/liboboe.so"
    INTERFACE_INCLUDE_DIRECTORIES "C:/Users/Administrator/.gradle/caches/8.9/transforms/57d1efb4069e1c11f03fe5f35f5a9859/transformed/oboe-1.7.0/prefab/modules/oboe/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

