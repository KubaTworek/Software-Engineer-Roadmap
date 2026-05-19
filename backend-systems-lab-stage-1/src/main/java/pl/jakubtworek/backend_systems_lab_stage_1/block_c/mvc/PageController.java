package pl.jakubtworek.backend_systems_lab_stage_1.block_c.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Classic MVC controller returning a view name.
 *
 * Unlike @RestController, this does not serialize response body to JSON.
 *
 * DispatcherServlet will use ViewResolver to resolve logical view name
 * to an actual template, for example Thymeleaf.
 */
@Controller
public class PageController {

    /**
     * Returns logical view name.
     *
     * Example:
     * "home" may be resolved to:
     * templates/home.html
     */
    @GetMapping("/home")
    public String home(Model model) {

        model.addAttribute("message", "Hello from Spring MVC");

        return "home";
    }
}