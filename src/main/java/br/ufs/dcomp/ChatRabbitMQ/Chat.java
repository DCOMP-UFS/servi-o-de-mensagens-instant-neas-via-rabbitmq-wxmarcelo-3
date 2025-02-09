package br.ufs.dcomp.ChatRabbitMQ;

import com.rabbitmq.client.*;

import java.io.IOException;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.Date;
import java.text.SimpleDateFormat;

public class Chat {
  
  private static final String HOST = "54.174.239.156";
  private static final String USER = "admin";
  private static final String PASSWORD = "password";
  
  private static String currentUser;
  private static String currentRecipient = "";

  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(HOST);
    factory.setUsername(USER);
    factory.setPassword(PASSWORD);
    factory.setVirtualHost("/");
    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();
    
    // Definir usuário atual
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    System.out.print("User: ");
    currentUser = reader.readLine();
    
    // Criar fila para receber mensagens
    channel.queueDeclare(currentUser, false, false, false, null);
    
    // Thread para ouvir mensagens recebidas
    Thread receiverThread = new Thread(() -> {
        try {
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                //ex: (21/09/2016 às 20:53) marciocosta diz: E aí, Tarcisio! Vamos sim!
                SimpleDateFormat sdf = new SimpleDateFormat("(dd/MM/yyyy 'às' HH:mm) ");
                String dataHora = delivery.getProperties().getTimestamp() != null ? sdf.format(delivery.getProperties().getTimestamp()) : " ";
                
                String message = new String(delivery.getBody(), "UTF-8");
                System.out.println("\n"+ dataHora + delivery.getProperties().getReplyTo() + " diz: " + message);
                System.out.print(currentRecipient + ">> ");
            };
            channel.basicConsume(currentUser, true, deliverCallback, consumerTag -> {});
        } catch (Exception e) {
            e.printStackTrace();
        }
    });
    receiverThread.start();
    
    while (true) {
    System.out.print((currentRecipient.isEmpty() ? "" : currentRecipient) + ">> ");
    String input = reader.readLine();

    // Alterar destinatário
    if (input.startsWith("@")) {
        currentRecipient = input;
    } else {
        // Enviar mensagem
        if (!currentRecipient.isEmpty()) {
            String recipient = currentRecipient.replace("@", "").trim();
            channel.basicPublish("", recipient, new AMQP.BasicProperties.Builder()
                    .replyTo(currentUser)
                    .timestamp(new Date())
                    .build(), input.getBytes("UTF-8"));
            } else {
                System.out.println("Selecione um destinatário com @usuario antes de enviar uma mensagem.");
          }
      }
    }
    
  }
}