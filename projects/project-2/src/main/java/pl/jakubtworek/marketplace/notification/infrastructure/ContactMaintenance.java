package pl.jakubtworek.marketplace.notification.infrastructure;

import pl.jakubtworek.marketplace.notification.application.ContactLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import java.nio.file.*;
import tools.jackson.databind.ObjectMapper;

/** Osobny proces bez HTTP/Kafka. Poświadczenia nie trafiają do argumentów ani logu. */
public final class ContactMaintenance {
    public static void main(String[] args)throws Exception {
        if(args.length!=3||!(args[0].equals("export")||args[0].equals("suppress")))throw new IllegalArgumentException("export|suppress registryFile manifestFile");
        var data=new DriverManagerDataSource(required("CONTACT_DB_URL"),required("CONTACT_DB_USER"),required("CONTACT_DB_PASSWORD"));
        var contacts=new ContactLifecycle(new JdbcTemplate(data),new DataSourceTransactionManager(data));
        Path body=Path.of(args[1]).toAbsolutePath().normalize(),manifest=Path.of(args[2]).toAbsolutePath().normalize();
        if(body.equals(manifest))throw new IllegalArgumentException("Registry and trusted manifest require distinct paths");
        var json=new ObjectMapper();
        if(args[0].equals("export")) {
            var registry=contacts.exportRegistry();
            atomicWrite(body,json.writeValueAsBytes(registry));atomicWrite(manifest,json.writeValueAsBytes(ContactLifecycle.seal(registry)));
            System.out.println("Registry exported: entries="+registry.entries().size()+", watermark="+registry.watermark());
        } else {
            if(!"true".equals(System.getenv("CONTACT_RESTORE_MAINTENANCE")))throw new IllegalStateException("Restore must remain closed to application traffic");
            contacts.suppressAfterRestore(json.readValue(body.toFile(),ContactLifecycle.Registry.class),json.readValue(manifest.toFile(),ContactLifecycle.RegistrySeal.class));
            System.out.println("Restore suppression completed; verify before opening traffic");
        }
    }
    private static String required(String name){String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalArgumentException("Missing "+name);return value;}
    private static void atomicWrite(Path destination,byte[] bytes)throws Exception {
        if(!Files.isDirectory(destination.getParent()))throw new IllegalArgumentException("Output parent must exist");
        Path temporary=Files.createTempFile(destination.getParent(),"registry-",".tmp");
        try{Files.write(temporary,bytes);Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temporary);}
    }
    private ContactMaintenance(){}
}
