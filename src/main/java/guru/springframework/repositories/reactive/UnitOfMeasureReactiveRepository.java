package guru.springframework.repositories.reactive;

import guru.springframework.domain.UnitOfMeasure;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/**
 * ReactiveMongoRepository<UnitOfMeasure, String> 
 * --> Mongo specific org.springframework.data.repository.
 *     Repository interface with reactive support.
 */
public interface UnitOfMeasureReactiveRepository extends ReactiveMongoRepository<UnitOfMeasure, String> {
}
