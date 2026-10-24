package pl.jakubtworek.marketplace.notification.api;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pl.jakubtworek.marketplace.notification.application.ContactLifecycle;
import java.util.*;

@RestController @Profile("postgres") @RequestMapping("/api/contacts/{customer}")
public class ContactController {
    private final ContactLifecycle contacts;
    public ContactController(ContactLifecycle contacts){this.contacts=contacts;}
    public record Request(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=255) String displayName){}
    @PutMapping @PreAuthorize("@orderOwnership.canCreate(#customer,authentication)")
    public void put(@PathVariable UUID customer,@Valid @RequestBody Request request){contacts.put(new ContactLifecycle.Contact(customer,request.email(),request.displayName()));}
    @GetMapping @PreAuthorize("@orderOwnership.canCreate(#customer,authentication)")
    public ContactLifecycle.Contact read(@PathVariable UUID customer){return contacts.read(customer).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));}
    @DeleteMapping @PreAuthorize("@orderOwnership.canCreate(#customer,authentication)")
    public Map<String,UUID> erase(@PathVariable UUID customer,@RequestHeader("Deletion-Request-Id") UUID request){return Map.of("requestId",contacts.erase(customer,request));}
}
