package ru.yubaba.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.yubaba.data.entity.*;
import ru.yubaba.data.enums.Role;
import ru.yubaba.data.repository.*;

import java.math.BigDecimal;

@Component
public class Bootstrap implements CommandLineRunner {
    private final AccountRepository accounts;
    private final RoomRepository rooms;
    private final IngredientRepository ingredients;
    private final ClientRepository clients;
    private final ServiceTemplateRepository templates;
    private final PasswordEncoder passwords;
    private final boolean demo;
    private final String password;

    public Bootstrap(AccountRepository accounts,
                     RoomRepository rooms,
                     IngredientRepository ingredients,
                     ClientRepository clients,
                     ServiceTemplateRepository templates,
                     PasswordEncoder passwords,
                     @Value("${app.demo}") boolean demo,
                     @Value("${app.bootstrap-password}") String password) {
        this.accounts = accounts;
        this.rooms = rooms;
        this.ingredients = ingredients;
        this.clients = clients;
        this.templates = templates;
        this.passwords = passwords;
        this.demo = demo;
        this.password = password;
    }

    private void account(String login, String name, Role role) {
        if (accounts.findByLogin(login).isEmpty()) {
            var a = new Account();
            a.login = login;
            a.name = name;
            a.role = role;
            a.password = passwords.encode(password);
            accounts.save(a);
        }
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (password.length() < 10 || password.length() > 72)
            throw new IllegalArgumentException("BOOTSTRAP_PASSWORD must contain 10–72 characters");
        account("haku", "Хаку", Role.MANAGER);
        if (!demo) return;
        account("lin", "Лин", Role.ADMIN);
        account("chihiro", "Тихиро", Role.ATTENDANT);
        account("rin", "Рин", Role.ATTENDANT);
        account("kamaji", "Камадзи", Role.BOILER);
        account("zeniba", "Дзениба", Role.ACCOUNTANT);
        if (rooms.count() == 0) {
            for (String name : new String[]{"Сосновая", "Лунная", "Нефритовая"}) {
                var r = new Room();
                r.name = name;
                r.bathType = name.equals("Нефритовая") ? "Большая купальня" : "Травяная купальня";
                r.capacity = name.equals("Нефритовая") ? 8 : 4;
                rooms.save(r);
            }
        }
        if (ingredients.count() == 0) {
            for (String[] row : new String[][]{{"Полынь", "г", "1200", "100", "2.50"}, {"Морская соль", "г", "5000", "500", "0.50"}, {"Масло хиноки", "мл", "400", "50", "8.00"}}) {
                var i = new Ingredient();
                i.name = row[0];
                i.unit = row[1];
                i.stock = new BigDecimal(row[2]);
                i.threshold = new BigDecimal(row[3]);
                i.price = new BigDecimal(row[4]);
                ingredients.save(i);
            }
        }
        if (clients.count() == 0) {
            var c = new Client();
            c.name = "Дух реки";
            c.contact = "river@example.test";
            c.notes = "Предпочитает травяные ванны";
            clients.save(c);
        }
        if (templates.count() == 0) {
            var all = ingredients.findAll();
            var t = new ServiceTemplate();
            t.name = "Дыхание леса";
            t.bathType = "Травяная купальня";
            t.preferredRoomId = rooms.findAll().get(0).id;
            t.attendants = 1;
            t.durationMinutes = 30;
            t.preparationMinutes = 10;
            t.breakMinutes = 10;
            t.temperature = 40;
            t.steps = "1. Нагреть воду до 40 °C.\n2. Добавить полынь и морскую соль.\n3. Перемешать, добавить масло хиноки.\n4. Проверить температуру и отметить готовность.";
            t.extraServices = "Травяной чай после купания";
            t.basePrice = new BigDecimal("1500.00");
            t.lines.add(new RecipeLine(all.get(0), new BigDecimal("50")));
            t.lines.add(new RecipeLine(all.get(1), new BigDecimal("100")));
            t.lines.add(new RecipeLine(all.get(2), new BigDecimal("5")));
            templates.save(t);
        }
    }
}
