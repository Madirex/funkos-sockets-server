package com.madirex.services.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.madirex.exceptions.io.DirectoryException;
import com.madirex.exceptions.io.ImportDataException;
import com.madirex.utils.LocalDateAdapter;
import com.madirex.utils.LocalDateTimeAdapter;
import com.madirex.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Clase BackupService
 */
public class BackupService<T> {

    private final Logger logger = LoggerFactory.getLogger(BackupService.class);

    /**
     * Constructor de la clase
     */
    public BackupService() {
        // Constructor vacío - Clase de servicio para exportación e importación de datos
    }

    /**
     * Exportar los datos pasados por parámetro a un archivo JSON
     *
     * @param path     Ruta del directorio donde se guardará el backup
     * @param fileName Nombre del archivo del backup
     * @param data     Datos a guardar
     */
    public Mono<Void> exportData(String path, String fileName, List<T> data) {
        return Mono.defer(() -> {
            File dataDir = new File(path);
            if (dataDir.exists()) {
                String dest = path + File.separator + fileName;
                Gson gson = new GsonBuilder()
                        .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                        .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                        .setPrettyPrinting()
                        .create();
                String json = gson.toJson(data);
                Utils.getInstance().writeString(dest, json);
                logger.debug("Backup realizado con éxito");
                return Mono.empty();
            } else {
                return Mono.error(new DirectoryException("No se creará el backup."));
            }
        });
    }


    /**
     * Importa los datos de un archivo JSON
     *
     * @param path     Ruta del archivo JSON
     * @param fileName Nombre del archivo JSON
     * @return Datos importados
     */
    public Flux<T> importData(String path, String fileName) {
        return Mono.fromCallable(() -> {
                    File folder = new File(path + File.separator);
                    if (!folder.exists()) {
                        throw new DirectoryException("No se creará el backup.");
                    }
                    File dataFile = new File(path + File.separator + fileName);

                    var bytes = Utils.getInstance().getFileBytes(dataFile);
                    String json = Arrays.toString(bytes);
                    Type listType = new TypeToken<List<T>>() {
                    }.getType();
                    Gson gson = new GsonBuilder()
                            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                            .create();
                    return gson.<List<T>>fromJson(json, listType);
                })
                .flatMapMany(Flux::fromIterable)
                .onErrorMap(ex -> new ImportDataException(ex.getMessage()));
    }
}
