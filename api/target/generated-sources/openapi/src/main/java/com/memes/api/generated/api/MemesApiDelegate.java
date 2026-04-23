package com.memes.api.generated.api;

import com.memes.api.generated.model.CategorySummary;
import com.memes.api.generated.model.ErrorResponse;
import com.memes.api.generated.model.Meme;
import com.memes.api.generated.model.MemePage;
import com.memes.api.generated.model.Stats;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.annotation.Generated;

/**
 * A delegate to be called by the {@link MemesApiController}}.
 * Implement this interface with a {@link org.springframework.stereotype.Service} annotated class.
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.7.0")
public interface MemesApiDelegate {

    default Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    /**
     * GET /memes/{category}/{slug} : Single meme by category and slug
     *
     * @param category  (required)
     * @param slug  (required)
     * @return OK (status code 200)
     *         or Meme not found (status code 404)
     * @see MemesApi#getMeme
     */
    default ResponseEntity<Meme> getMeme(String category,
        String slug) {
        getRequest().ifPresent(request -> {
            for (MediaType mediaType: MediaType.parseMediaTypes(request.getHeader("Accept"))) {
                if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                    String exampleString = "{ \"score\" : 0, \"post_url\" : \"post_url\", \"author\" : \"author\", \"image_path\" : \"image_path\", \"description\" : \"description\", \"created_at\" : \"2000-01-23T04:56:07.000+00:00\", \"category\" : \"category\", \"title\" : \"title\", \"slug\" : \"slug\", \"subreddit\" : \"subreddit\", \"source_url\" : \"source_url\", \"tags\" : [ \"tags\", \"tags\" ] }";
                    ApiUtil.setExampleResponse(request, "application/json", exampleString);
                    break;
                }
                if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                    String exampleString = "{ \"error\" : \"error\", \"message\" : \"message\" }";
                    ApiUtil.setExampleResponse(request, "application/json", exampleString);
                    break;
                }
            }
        });
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }

    /**
     * GET / : Global collection statistics
     *
     * @return OK (status code 200)
     * @see MemesApi#getStats
     */
    default ResponseEntity<Stats> getStats() {
        getRequest().ifPresent(request -> {
            for (MediaType mediaType: MediaType.parseMediaTypes(request.getHeader("Accept"))) {
                if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                    String exampleString = "{ \"total_categories\" : 6, \"total_memes\" : 0, \"indexed_at\" : \"2000-01-23T04:56:07.000+00:00\", \"total_subreddits\" : 1, \"top_category\" : \"top_category\" }";
                    ApiUtil.setExampleResponse(request, "application/json", exampleString);
                    break;
                }
            }
        });
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }

    /**
     * GET /categories : All categories with meme counts and top score
     *
     * @return OK (status code 200)
     * @see MemesApi#listCategories
     */
    default ResponseEntity<List<CategorySummary>> listCategories() {
        getRequest().ifPresent(request -> {
            for (MediaType mediaType: MediaType.parseMediaTypes(request.getHeader("Accept"))) {
                if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                    String exampleString = "[ { \"top_score\" : 6, \"count\" : 0, \"category\" : \"category\" }, { \"top_score\" : 6, \"count\" : 0, \"category\" : \"category\" } ]";
                    ApiUtil.setExampleResponse(request, "application/json", exampleString);
                    break;
                }
            }
        });
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }

    /**
     * GET /memes : Paginated list of memes with optional filters
     *
     * @param page  (optional, default to 0)
     * @param limit  (optional, default to 20)
     * @param category  (optional)
     * @param subreddit  (optional)
     * @param sort  (optional, default to score)
     * @return OK (status code 200)
     * @see MemesApi#listMemes
     */
    default ResponseEntity<MemePage> listMemes(Integer page,
        Integer limit,
        String category,
        String subreddit,
        String sort) {
        getRequest().ifPresent(request -> {
            for (MediaType mediaType: MediaType.parseMediaTypes(request.getHeader("Accept"))) {
                if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                    String exampleString = "{ \"total\" : 5, \"data\" : [ { \"score\" : 0, \"post_url\" : \"post_url\", \"author\" : \"author\", \"image_path\" : \"image_path\", \"description\" : \"description\", \"created_at\" : \"2000-01-23T04:56:07.000+00:00\", \"category\" : \"category\", \"title\" : \"title\", \"slug\" : \"slug\", \"subreddit\" : \"subreddit\", \"source_url\" : \"source_url\", \"tags\" : [ \"tags\", \"tags\" ] }, { \"score\" : 0, \"post_url\" : \"post_url\", \"author\" : \"author\", \"image_path\" : \"image_path\", \"description\" : \"description\", \"created_at\" : \"2000-01-23T04:56:07.000+00:00\", \"category\" : \"category\", \"title\" : \"title\", \"slug\" : \"slug\", \"subreddit\" : \"subreddit\", \"source_url\" : \"source_url\", \"tags\" : [ \"tags\", \"tags\" ] } ], \"limit\" : 1, \"page\" : 6, \"total_pages\" : 5 }";
                    ApiUtil.setExampleResponse(request, "application/json", exampleString);
                    break;
                }
            }
        });
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }

    /**
     * GET /memes/{category} : All memes in a specific category
     *
     * @param category  (required)
     * @param page  (optional, default to 0)
     * @param limit  (optional, default to 20)
     * @param sort  (optional, default to score)
     * @return OK (status code 200)
     *         or Category not found (status code 404)
     * @see MemesApi#listMemesByCategory
     */
    default ResponseEntity<MemePage> listMemesByCategory(String category,
        Integer page,
        Integer limit,
        String sort) {
        getRequest().ifPresent(request -> {
            for (MediaType mediaType: MediaType.parseMediaTypes(request.getHeader("Accept"))) {
                if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                    String exampleString = "{ \"total\" : 5, \"data\" : [ { \"score\" : 0, \"post_url\" : \"post_url\", \"author\" : \"author\", \"image_path\" : \"image_path\", \"description\" : \"description\", \"created_at\" : \"2000-01-23T04:56:07.000+00:00\", \"category\" : \"category\", \"title\" : \"title\", \"slug\" : \"slug\", \"subreddit\" : \"subreddit\", \"source_url\" : \"source_url\", \"tags\" : [ \"tags\", \"tags\" ] }, { \"score\" : 0, \"post_url\" : \"post_url\", \"author\" : \"author\", \"image_path\" : \"image_path\", \"description\" : \"description\", \"created_at\" : \"2000-01-23T04:56:07.000+00:00\", \"category\" : \"category\", \"title\" : \"title\", \"slug\" : \"slug\", \"subreddit\" : \"subreddit\", \"source_url\" : \"source_url\", \"tags\" : [ \"tags\", \"tags\" ] } ], \"limit\" : 1, \"page\" : 6, \"total_pages\" : 5 }";
                    ApiUtil.setExampleResponse(request, "application/json", exampleString);
                    break;
                }
                if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                    String exampleString = "{ \"error\" : \"error\", \"message\" : \"message\" }";
                    ApiUtil.setExampleResponse(request, "application/json", exampleString);
                    break;
                }
            }
        });
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }

    /**
     * GET /search : Full-text search across title and description (FTS5)
     *
     * @param q  (required)
     * @param page  (optional, default to 0)
     * @param limit  (optional, default to 20)
     * @return OK (status code 200)
     *         or Missing or empty q parameter (status code 400)
     * @see MemesApi#searchMemes
     */
    default ResponseEntity<MemePage> searchMemes(String q,
        Integer page,
        Integer limit) {
        getRequest().ifPresent(request -> {
            for (MediaType mediaType: MediaType.parseMediaTypes(request.getHeader("Accept"))) {
                if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                    String exampleString = "{ \"total\" : 5, \"data\" : [ { \"score\" : 0, \"post_url\" : \"post_url\", \"author\" : \"author\", \"image_path\" : \"image_path\", \"description\" : \"description\", \"created_at\" : \"2000-01-23T04:56:07.000+00:00\", \"category\" : \"category\", \"title\" : \"title\", \"slug\" : \"slug\", \"subreddit\" : \"subreddit\", \"source_url\" : \"source_url\", \"tags\" : [ \"tags\", \"tags\" ] }, { \"score\" : 0, \"post_url\" : \"post_url\", \"author\" : \"author\", \"image_path\" : \"image_path\", \"description\" : \"description\", \"created_at\" : \"2000-01-23T04:56:07.000+00:00\", \"category\" : \"category\", \"title\" : \"title\", \"slug\" : \"slug\", \"subreddit\" : \"subreddit\", \"source_url\" : \"source_url\", \"tags\" : [ \"tags\", \"tags\" ] } ], \"limit\" : 1, \"page\" : 6, \"total_pages\" : 5 }";
                    ApiUtil.setExampleResponse(request, "application/json", exampleString);
                    break;
                }
                if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                    String exampleString = "{ \"error\" : \"error\", \"message\" : \"message\" }";
                    ApiUtil.setExampleResponse(request, "application/json", exampleString);
                    break;
                }
            }
        });
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }

}
