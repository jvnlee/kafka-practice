package com.example.kafka;

import com.github.javafaker.Faker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static java.time.LocalDateTime.now;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Locale.KOREAN;

public class PizzaMessage {

    private static final List<String> pizzaNames = List.of(
            "Potato", "Cheese", "Garlic", "Supreme", "Peperoni"
    );

    private static final List<String> shopIds = List.of(
            "A001", "B001", "C001", "D001", "E001", "F001",
            "G001", "H001", "I001", "J001", "K001", "L001",
            "M001", "N001", "O001", "P001", "Q001", "R001"
    );

    private static final Random random = new Random(1028);

    private static final Faker faker = Faker.instance(random);

    private String getRandomValue(List<String> list) {
        int size = list.size();
        int index = random.nextInt(size);

        return list.get(index);
    }

    public Map<String, String> produceMessage(int id) {
        String orderId = id + "";
        String shopId = getRandomValue(shopIds);
        String pizzaName = getRandomValue(pizzaNames);
        String customerName = faker.name().fullName();
        String phoneNumber = faker.phoneNumber().phoneNumber();
        String address = faker.address().streetAddress();

        String message = String.format(
                "\n-order_id: %s \n-shop: %s \n-pizza_name: %s \n-customer_name: %s \n-phone_number: %s \n-address: %s \n-time: %s\n",
                orderId, shopId, pizzaName, customerName, phoneNumber, address,
                now().format(ofPattern("yyyy-MM-dd HH:mm:ss", KOREAN))
        );

        Map<String, String> messageMap = new HashMap<>();
        messageMap.put(shopId, message);
        return messageMap;
    }

    public static void main(String[] args) {
        PizzaMessage pizzaMessage = new PizzaMessage();

        for (int i = 0; i < 60; i++) {
            Map<String, String> messageMap = pizzaMessage.produceMessage(i);
            System.out.println(messageMap);
        }
    }
}
