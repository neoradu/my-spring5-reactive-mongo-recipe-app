package guru.springframework.repositories.reactive;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import guru.springframework.domain.Category;

public interface CategoryReactiveRepository extends ReactiveMongoRepository<Category, String> {

}
