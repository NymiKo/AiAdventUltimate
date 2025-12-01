#!/bin/bash

cd "$(dirname "$0")"

if [ -f .env ]; then
    echo "Загружаем переменные окружения из .env..."
    export $(cat .env | grep -v '^#' | xargs)
    echo "✓ Переменные загружены"
    echo ""
else
    echo "⚠️ Файл .env не найден!"
    exit 1
fi

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

echo "Запускаем приложение с голосовым вводом..."
echo "YANDEX_API_KEY установлен: ${YANDEX_API_KEY:0:10}..."
echo "YANDEX_FOLDER_ID установлен: $YANDEX_FOLDER_ID"
echo ""

./gradlew composeApp:run

