package ru.yubaba.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yubaba.controller.dto.ClientInput;
import ru.yubaba.data.entity.Client;
import ru.yubaba.data.repository.ClientRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static ru.yubaba.service.ServiceChecks.*;

@Service
@Transactional(readOnly = true)
public class ClientService {
    private final ClientRepository clientRepository;

    public ClientService(
            ClientRepository clientRepository
    ) {
        this.clientRepository = clientRepository;
    }

    public List<Client> getClients(String query) {
        String q = query.toLowerCase().trim();
        return clientRepository.findAll().stream()
                .filter(c -> (c.name + " " + c.contact).toLowerCase().contains(q))
                .sorted(Comparator.comparing(c -> c.name))
                .toList();
    }

    @Transactional
    public Client saveClient(Long id, ClientInput input) {
        Client client = id == null ? new Client() : clientRepository.findById(id).orElseThrow(ServiceChecks::createNotFoundException);
        if (id != null) {
            compareVersion(client.version, input.version());
        }
        client.name = input.name().trim();
        client.contact = input.contact().trim();
        client.notes = Objects.requireNonNullElse(input.notes(), "").trim();
        check(!client.name.isBlank() && !client.contact.isBlank(), "Укажите имя и контакт клиента.");
        return clientRepository.saveAndFlush(client);
    }
}
