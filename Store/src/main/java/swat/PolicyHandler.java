package swat;

import swat.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }
    
    @Autowired
    StoreRepository storeRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPayed_(@Payload Payed payed){

    	if(payed.isMe()){
            System.out.println("##### listener  : " + payed.toJson());
            
            Store store = new Store();
            store.setMenuId(payed.getMenuId());
            store.setOrderId(payed.getOrderId());
            store.setQty(payed.getQty());
            store.setUserId(payed.getUserId());
            
            storeRepository.save(store);
        }
    }

}
