#include <vulkan/vulkan.h>

extern "C" VKAPI_ATTR void VKAPI_CALL vkGetPhysicalDeviceFeatures2(
    VkPhysicalDevice physicalDevice,
    VkPhysicalDeviceFeatures2 * features
) {
    using Features2Fn = PFN_vkGetPhysicalDeviceFeatures2;
    static Features2Fn resolved = nullptr;
    static bool attempted = false;

    if (!attempted) {
        resolved = reinterpret_cast<Features2Fn>(
            vkGetInstanceProcAddr(VK_NULL_HANDLE, "vkGetPhysicalDeviceFeatures2")
        );
        if (resolved == nullptr) {
            resolved = reinterpret_cast<Features2Fn>(
                vkGetInstanceProcAddr(VK_NULL_HANDLE, "vkGetPhysicalDeviceFeatures2KHR")
            );
        }
        attempted = true;
    }

    if (resolved != nullptr) {
        resolved(physicalDevice, features);
        return;
    }

    if (features != nullptr) {
        vkGetPhysicalDeviceFeatures(physicalDevice, &features->features);
    }
}
