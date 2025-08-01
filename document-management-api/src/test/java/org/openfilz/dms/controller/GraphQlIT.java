package org.openfilz.dms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.context.TestConstructor.AutowireMode.ALL;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = ALL)
public class GraphQlIT extends TestContainersBaseConfig {



    public GraphQlIT(WebTestClient webTestClient) {
        super(webTestClient);
        tester = HttpGraphQlTester.create(webTestClient);
    }

    @DynamicPropertySource
    static void registerResourceServerIssuerProperty(DynamicPropertyRegistry registry) {
    }

    private final HttpGraphQlTester tester;


    @Test
    void testListFolder() {

        //tester.


    }
/* @Test
    void createPostAndAddCommentAndSubscription() {
        //create post
        var createPostQuery = """
                mutation createPost($input: CreatePostInput!){
                    createPost(createPostInput:$input){
                        id
                        title
                        content
                    }
                }""".trim();

        Map<String, Object> createPostVariables = Map.of(
                "input", Map.of(
                        "title", "my post created by Spring GraphQL",
                        "content", "content of my post"
                )
        );

        var countDownLatch = new CountDownLatch(1);
        var postIdReference = new AtomicReference<String>();
        this.client.document(createPostQuery).variables(createPostVariables).execute()
                .map(response -> objectMapper.convertValue(
                        response.<Map<String, Object>>getData().get("createPost"),
                        Post.class)
                )
                .subscribe(post -> {
                    log.info("created post: {}", post);
                    postIdReference.set(post.getId());
                    countDownLatch.countDown();
                });

        countDownLatch.await(5, SECONDS);

        String postId = postIdReference.get();
        log.debug("created post id: {}", postId);
        assertThat(postId).isNotNull();

        var postById = """
                query post($postId:String!){
                   postById(postId:$postId) {
                     id
                     title
                     content
                   }
                 }""".trim();
        this.client.document(postById).variable("postId", postId)
                .execute()
                .as(StepVerifier::create)
                .consumeNextWith(response -> {
                    var post = objectMapper.convertValue(
                            response.<Map<String, Object>>getData().get("postById"),
                            Post.class);
                    assertThat(post).isNotNull();
                    assertThat(post.getId()).isEqualTo(postId);
                    assertThat(post.getTitle()).isEqualTo("my post created by Spring GraphQL");
                    assertThat(post.getContent()).isEqualTo("content of my post");
                })
                .verifyComplete();


        var subscriptionQuery = "subscription onCommentAdded { commentAdded { id content } }";
        Flux<Comment> result = this.client.document(subscriptionQuery)
                .executeSubscription()
                .map(response -> objectMapper.convertValue(
                        response.<Map<String, Object>>getData().get("commentAdded"),
                        Comment.class)
                );

        var verify = StepVerifier.create(result)
                .consumeNextWith(c -> assertThat(c.getContent()).startsWith("comment of my post at "))
                .consumeNextWith(c -> assertThat(c.getContent()).startsWith("comment of my post at "))
                .consumeNextWith(c -> assertThat(c.getContent()).startsWith("comment of my post at "))
                .thenCancel().verifyLater();

        addCommentToPost(postId);
        addCommentToPost(postId);
        addCommentToPost(postId);

        verify.verify();
    }*/


}
