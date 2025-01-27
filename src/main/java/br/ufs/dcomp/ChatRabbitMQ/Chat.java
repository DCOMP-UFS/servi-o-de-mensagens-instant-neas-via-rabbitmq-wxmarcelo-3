package br.ufs.dcomp.ChatRabbitMQ;

import com.rabbitmq.client.*;

import java.io.IOException;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Chat {
  
  private static final String HOST = "18.212.222.26";
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
                String message = new String(delivery.getBody(), "UTF-8");
                System.out.println("\nMensagem recebida de (" + delivery.getProperties().getReplyTo() + "): " + message);
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
                    .build(), input.getBytes("UTF-8"));
            } else {
                System.out.println("Selecione um destinatário com @usuario antes de enviar uma mensagem.");
          }
      }
    }
    
  }
}