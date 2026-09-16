package ru.yubaba.service;

import org.springframework.http.HttpStatus;
import ru.yubaba.service.exception.BusinessException;

class ServiceChecks {
    private ServiceChecks() {
    }

    static BusinessException createNotFoundException() {
        return new BusinessException(HttpStatus.NOT_FOUND, "Запись не найдена.");
    }

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new BusinessException(message);
        }
    }

    static void compareVersion(long actual, Long expected) {
        check(expected != null && actual == expected, "Данные изменены другим сотрудником. Обновите страницу.");
    }
}
