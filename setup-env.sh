#!/bin/bash

echo "=========================================="
echo "Настройка переменных окружения для голосового ввода"
echo "=========================================="
echo ""

echo "Введите ваш YANDEX_API_KEY (или 'test' для тестирования):"
read -r api_key

echo "Введите ваш YANDEX_FOLDER_ID (или 'test' для тестирования):"
read -r folder_id

export YANDEX_API_KEY="$api_key"
export YANDEX_FOLDER_ID="$folder_id"

echo ""
echo "✓ Переменные установлены!"
echo ""
echo "YANDEX_API_KEY=$YANDEX_API_KEY"
echo "YANDEX_FOLDER_ID=$YANDEX_FOLDER_ID"
echo ""
echo "Запускаем приложение..."
echo ""

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew composeApp:run

