package guru.springframework.services;

import guru.springframework.commands.IngredientCommand;
import guru.springframework.converters.IngredientCommandToIngredient;
import guru.springframework.converters.IngredientToIngredientCommand;
import guru.springframework.domain.Ingredient;
import guru.springframework.domain.Recipe;
import guru.springframework.repositories.RecipeRepository;
import guru.springframework.repositories.UnitOfMeasureRepository;
import guru.springframework.repositories.reactive.RecipeReactiveRepository;
import guru.springframework.repositories.reactive.UnitOfMeasureReactiveRepository;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Created by jt on 6/28/17.
 */
@Slf4j
@Service
public class IngredientServiceImpl implements IngredientService {

    private final IngredientToIngredientCommand ingredientToIngredientCommand;
    private final IngredientCommandToIngredient ingredientCommandToIngredient;
    private final RecipeReactiveRepository recipeRepository;
    private final UnitOfMeasureReactiveRepository unitOfMeasureRepository;
    //this is needed for delete since @DB ref is not suported by raective repository
    private RecipeRepository nonReactiveRecipeRepository;

    public IngredientServiceImpl(IngredientToIngredientCommand ingredientToIngredientCommand,
                                 IngredientCommandToIngredient ingredientCommandToIngredient,
                                 RecipeReactiveRepository recipeRepository, 
                                 UnitOfMeasureReactiveRepository unitOfMeasureRepository,
                                 RecipeRepository nonReactiveRecipeRepository) {
        this.ingredientToIngredientCommand = ingredientToIngredientCommand;
        this.ingredientCommandToIngredient = ingredientCommandToIngredient;
        this.recipeRepository = recipeRepository;
        this.unitOfMeasureRepository = unitOfMeasureRepository;
        this.nonReactiveRecipeRepository = nonReactiveRecipeRepository;
    }
    private Ingredient findIngredientById(String id, Recipe recipe) {
    	Optional<Ingredient> ingredientOpt = recipe.getIngredients()
                                                   .stream()
                                                   .filter(ingredient -> ingredient.getId().equals(id))
                                                   .findFirst();
    	if(!ingredientOpt.isPresent())
    		throw new RuntimeException(String.format("Igredient id:%s not sound in recipe id:%s", id, recipe.getId()));
    	return ingredientOpt.get();
    }
    
    @Override
    public Mono<IngredientCommand> findByRecipeIdAndIngredientId(String recipeId, String ingredientId) {

        Mono<Recipe> recipeMono= recipeRepository.findById(recipeId);
        /*Recipe recipe = recipeOptional.block();
        if (recipe == null){
            //todo impl error handling
            log.error("recipe id not found. Id: " + recipeId);
            throw new RuntimeException(String.format("recipe id:%s not found!", recipeId));
        }*/

        /*Mono<IngredientCommand> ingredientCmdMono = recipeMono.map(recipe -> (findIngredientById(recipeId, recipe)))
        													  .map(ingredientToIngredientCommand::convert)
        													  .map((ingredientCommand) -> { ingredientCommand.setRecipeId(recipeId);
        													                                return ingredientCommand;
        													                               });*/
        Mono<IngredientCommand> ingredientCmdMono = 
        		                recipeMono.flatMapIterable(Recipe::getIngredients)
        							      .filter(ingredient -> ingredient.getId().equals(ingredientId))
        								  .single()
				                          .map(ingredientToIngredientCommand::convert)
				                          .map((ingredientCommand) -> { ingredientCommand.setRecipeId(recipeId);
				                                return ingredientCommand;
				                           });

        /*Optional<IngredientCommand> ingredientCommandOptional = recipe.getIngredients().stream()
                .filter(ingredient -> ingredient.getId().equals(ingredientId))
                .map( ingredient -> ingredientToIngredientCommand.convert(ingredient)).findFirst();
		*/

        return ingredientCmdMono;
    }

    @Override
    public Mono<IngredientCommand> saveIngredientCommand(IngredientCommand command) {
        
       /* Mono<Recipe> recipeMono = recipeRepository.findById(command.getRecipeId());
        Mono<IngredientCommand> cmdMono = 
        recipeMono.map((recipe) -> {
        	          return findIngredientById(command.getId(), recipe);})
        		  .map((ingredient)-> {
        			  ingredient.setDescription(command.getDescription());
        			  ingredient.setAmount(command.getAmount());
        			  ingredient.setUom(unitOfMeasureRepository
                                         .findById(command.getUom().getId()).block());
        			  return ingredient; })
        		   .map(ingredientToIngredientCommand::convert);
       return cmdMono;*/
    	//This is not reactive code!! just blocking like shit!
        Optional<Recipe> recipeOptional = recipeRepository.findById(command.getRecipeId()).blockOptional();
        if(!recipeOptional.isPresent()) {

            //todo toss error if not found!
            log.error("Recipe not found for id: " + command.getRecipeId());
            return Mono.just(new IngredientCommand());
        } else {
            Recipe recipe = recipeOptional.get();

            Optional<Ingredient> ingredientOptional = recipe
                    .getIngredients()
                    .stream()
                    .filter(ingredient -> ingredient.getId().equals(command.getId()))
                    .findFirst();

            if(ingredientOptional.isPresent()){
                Ingredient ingredientFound = ingredientOptional.get();
                ingredientFound.setDescription(command.getDescription());
                ingredientFound.setAmount(command.getAmount());
                ingredientFound.setUom(unitOfMeasureRepository
                        .findById(command.getUom().getId())
                        .blockOptional()
                        .orElseThrow(() -> new RuntimeException("UOM NOT FOUND"))); //todo address this
            } else {
                //add new Ingredient
                Ingredient ingredient = ingredientCommandToIngredient.convert(command);
              //  ingredient.setRecipe(recipe);
                recipe.addIngredient(ingredient);
            }

            Recipe savedRecipe = recipeRepository.save(recipe).block();

            Optional<Ingredient> savedIngredientOptional = savedRecipe.getIngredients().stream()
                    .filter(recipeIngredients -> recipeIngredients.getId().equals(command.getId()))
                    .findFirst();

            //check by description
            if(!savedIngredientOptional.isPresent()){
                //not totally safe... But best guess
                savedIngredientOptional = savedRecipe.getIngredients().stream()
                        .filter(recipeIngredients -> recipeIngredients.getDescription().equals(command.getDescription()))
                        .filter(recipeIngredients -> recipeIngredients.getAmount().equals(command.getAmount()))
                        .filter(recipeIngredients -> recipeIngredients.getUom().getId().equals(command.getUom().getId()))
                        .findFirst();
            }

            //todo check for fail

            //enhance with id value
            IngredientCommand ingredientCommandSaved = ingredientToIngredientCommand.convert(savedIngredientOptional.get());
            ingredientCommandSaved.setRecipeId(recipe.getId());
			
            return Mono.just(ingredientCommandSaved);
        }
    }

    @Override
    public Mono<Void> deleteById(String recipeId, String idToDelete) {

        log.debug("Deleting ingredient: " + recipeId + ":" + idToDelete);

        Optional<Recipe> recipeOptional = nonReactiveRecipeRepository.findById(recipeId);

        if(recipeOptional.isPresent()){
            Recipe recipe = recipeOptional.get();
            log.debug("found recipe");

            Optional<Ingredient> ingredientOptional = recipe
                    .getIngredients()
                    .stream()
                    .filter(ingredient -> ingredient.getId().equals(idToDelete))
                    .findFirst();

            if(ingredientOptional.isPresent()){
                log.debug("found Ingredient");
                //Ingredient ingredientToDelete = ingredientOptional.get();
               // ingredientToDelete.setRecipe(null);
                recipe.getIngredients().remove(ingredientOptional.get());
                nonReactiveRecipeRepository.save(recipe);
            }
        } else {
            log.debug("Recipe Id Not found. Id:" + recipeId);
        }
        return Mono.empty();
    }
}
