#include "led_heartbeat.h"

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/gpio.h"
#include "esp_log.h"

#define TAG "heartbeat"

#define HEARTBEAT_LED_GPIO    27
#define HEARTBEAT_PERIOD_MS   5000
#define HEARTBEAT_FLASH_MS    10

static void heartbeat_task(void *arg)
{
    (void)arg;

    while (1) {
        gpio_set_level(HEARTBEAT_LED_GPIO, 0);
        vTaskDelay(pdMS_TO_TICKS(HEARTBEAT_FLASH_MS));
        gpio_set_level(HEARTBEAT_LED_GPIO, 1);
        ESP_LOGI(TAG, "tick");
        vTaskDelay(pdMS_TO_TICKS(HEARTBEAT_PERIOD_MS - HEARTBEAT_FLASH_MS));
    }
}

void led_heartbeat_init(void)
{
    ESP_ERROR_CHECK(gpio_reset_pin(HEARTBEAT_LED_GPIO));
    ESP_ERROR_CHECK(gpio_set_direction(HEARTBEAT_LED_GPIO, GPIO_MODE_OUTPUT_OD));
    gpio_set_level(HEARTBEAT_LED_GPIO, 0);

    BaseType_t ret = xTaskCreate(
        heartbeat_task,
        "heartbeat",
        2048,
        NULL,
        1,
        NULL
    );
    if (ret != pdPASS) {
        ESP_LOGE(TAG, "Failed to create heartbeat task");
    } else {
        ESP_LOGI(TAG, "Heartbeat task started (LED on GPIO%d, %d ms flash every %d ms)",
                 HEARTBEAT_LED_GPIO, HEARTBEAT_FLASH_MS, HEARTBEAT_PERIOD_MS);
    }
}
