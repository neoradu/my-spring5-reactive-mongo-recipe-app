package guru.springframework.repositories.reactive;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.junit4.SpringRunner;

import guru.springframework.domain.UnitOfMeasure;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@RunWith(SpringRunner.class)
@Slf4j
@DataMongoTest
public class UnitOfMeasureReactiveRepositoryIT {
	
	private class UnitOfMeasureSubscriber implements Subscriber<UnitOfMeasure> {
		private long requestCount;
		private List<UnitOfMeasure> producedValues;
		
		private UnitOfMeasureSubscriber(long requestCount) {
			this.requestCount = requestCount;
			producedValues = new ArrayList<>();
		}
		
		@Override
		public void onSubscribe(Subscription s) {
			log.debug(String.format("onSubscribe(%d)", requestCount));
			s.request(requestCount);
		}

		@Override
		public void onNext(UnitOfMeasure t) {
			log.debug(String.format("onNext(%s)", t.getDescription()));
			producedValues.add(t);
		}

		@Override
		public void onError(Throwable t) {
			log.debug("onError() " + t.toString());
		}

		@Override
		public void onComplete() {
			log.debug("onComplete()");
		}

		public List<UnitOfMeasure> getProducedValues() {
			return producedValues;
		}
		
		public void waitForCompletion() throws InterruptedException {
			int cnt = 1000;
			while((cnt-- > 0) && (producedValues.size() != requestCount)) {
				Thread.sleep(10);
			}
		}
	}
	
	private static UnitOfMeasure uom0;
	private static UnitOfMeasure uom1;
	private static UnitOfMeasure uom2;
	private static List<UnitOfMeasure> uomList;
	
	@Autowired
	public UnitOfMeasureReactiveRepository repository;
	
	private static long UOM_CNT = 4;
	
	@BeforeClass
	public static void setUpd() {
		uom0 = new UnitOfMeasure();
		uom0.setDescription("Galeata");
		uom1 = new UnitOfMeasure();
		uom1.setDescription("cana");
		uom2 = new UnitOfMeasure();
		uom2.setDescription("lighean");
		
		uomList = new ArrayList<>();
		for (long i = 0; i < UOM_CNT; i++) {
			UnitOfMeasure uom = new UnitOfMeasure();
			uom.setDescription(String.format("uom-%d", i));
			uomList.add(uom);
		}
		

	}
	
	@Before
	public void setUpTest() {
		Flux<UnitOfMeasure> uomFlux = repository.insert(uomList);
		log.debug("After repository.insert(uomList)");
		uomFlux.blockLast();
		log.debug("uomFlux.blockLast();");	
	}
	
	@After
	public void cleanAfterTest() {
		repository.deleteAll().block();
	}

	@Test
	public void testDeleteOne() throws Exception {
		repository.delete(uomList.get(0)).block();
		Mono<Long> uomMono = repository.count();
		log.debug("after:  repository.count();");
		long returnedCnt = uomMono.block();
		
		assertEquals(UOM_CNT - 1, returnedCnt);
	}
	
	@Test
	public void testDelete() throws Exception {
		repository.deleteAll().block();
		Mono<Long> uomMono = repository.count();
		log.debug("after:  repository.count();");
		long returnedCnt = uomMono.block();
		
		assertEquals(0, returnedCnt);
	}
	
	@Test
	public void testFindAll() throws Exception {
		
		Flux<UnitOfMeasure> uomFlux = repository.findAll();
		
		UnitOfMeasureSubscriber uomS = new UnitOfMeasureSubscriber(UOM_CNT);
		uomFlux.subscribe(uomS);
		
		//This is async! so we wait for completion
		//We could also use uomFlux.blockFirst()
		uomS.waitForCompletion();
		List<UnitOfMeasure> uomList = uomS.getProducedValues();
		assertEquals(UOM_CNT, uomList.size());
		assertEquals("uom-0", uomList.get(0).getDescription());		
	}

	@Test
	public void testFindByDescription() throws Exception {
		
		Mono<UnitOfMeasure> uomMono = repository.findByDescription("uom-0");
		log.debug("after: repository.findByDescription(\"uom-0\");");
		UnitOfMeasure found = uomMono.block();
		
		assertEquals("uom-0", found.getDescription());
	}
	
	@Test
	public void testCount() throws Exception {
		
		Mono<Long> uomMono = repository.count();
		log.debug("after:  repository.count();");
		long returnedCnt = uomMono.block();
		
		assertEquals(UOM_CNT, returnedCnt);
	}
	

}
