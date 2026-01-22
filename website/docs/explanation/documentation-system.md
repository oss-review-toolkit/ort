# Documentation System

ORT documentation is organized using the [Diátaxis framework][diátaxis]. Below we provide a summary of the Diátaxis and why it works well, If you are interested in learning more, we recommend you to visiting the [Diátaxis][diátaxis] site for the full details.

## Diátaxis framework

The Diátaxis framework was developed over the years by [Daniele Procida][daniele-procida]. It serves as a mental model for organizing and managing documentation in a way that simplifies writing, maintenance, and navigation.

Daniele began developing the system while at Divio, so you may also encounter it referred to as [Divio's The Documentation System][divio-documentation-system].
It appears that he has drawn significantly from Jacob Kaplan-Moss's Writing Great Documentation: What to Write. Given their collaboration at Django, it's not unexpected that Jacob's concepts would have had an impact on Daniele.

## Why did we adopt Diátaxis?

Let's start with these quotes from the [Divio's The Documentation System introduction page](https://docs.divio.com/documentation-system/introduction/#the-problem-it-solves):

> It doesn't matter how good your product or software project is, because if its documentation is not good enough, people will not use it. Even if they have to use it because they have no choice, without good documentation, they won't use it effectively or the way you'd like them to.
>
> Nearly everyone knows that they need good documentation, and most people try to create good documentation. And most people fail. Usually, it's not because they don't try hard enough. Usually, it's because they are not doing it the right way. This system is a way to make your documentation better, not by working harder at it, but by doing it the right way. The right way is the easier way - easier to write, and easier to maintain.

This is the main reason why we adopted Diátaxis. Writing documentation always seemed like a struggle, and we were looking for a better way. Using this framework has made it much easier and more enjoyable to write ORT's documentation.

## How does Diátaxis work?

The system is founded on the concept that there are four distinct types of documentation rather than just one. Each type must be crafted differently and is utilized in various contexts. All of them are essential, but they serve different phases of the user's experience.

The four categories of documentation include [Tutorials][diátaxis-tutorials], [How-to guides][diátaxis-how-to-guides], [Reference][diátaxis-reference], and [Explanation][diátaxis-explanation]. Each type fulfills a specific function, and the purposes are well-defined. Here’s a helpful visual that illustrates the system, which we copied from the [Diátaxis website][diátaxis-expectations-and-guidance]:

| Type          | What They Do              | Answers the Question   | Oriented to   | Purpose                            | Form                   | Analogy                                  |
|---------------|---------------------------|------------------------|---------------|------------------------------------|------------------------|------------------------------------------|
| Tutorials     | Introduce, educate, lead  | Can you teach me to …? | Learning      | To provide a learning experience   | A lesson               | Teaching a child how to cook             |
| How-to Guides | Guide                     | How do I …?            | Goals         | To help achieve a particular goal  | A series of steps      | A recipe in a cookery book               |
| Reference     | State, describe, inform   | What is …              | Information   | To describe the machinery          | Dry description        | Information on the back of a food packet |
| Explanations  | Explain, clarify, discuss | Why …                  | Understanding | To illuminate a topic              | Discursive explanation | An article on culinary social history    |

## Why four documentation categories?

Without getting too deep into the details (check the [Diátaxis Foundations][diátaxis-foundations] for a deep dive), Diátaxis makes the argument that a skill will need to be both **acquired** and then later **applied**. Mastering a skill requires both **action** (practical knowledge) and **cognition** (theoretical knowledge).
Depending on the way the user is trying to improve their skills, they will require various types of documentation, see table below copied from [Diataxis Foundations].

| Need           | Addressed In     | The User                   | The Documentation    |
|----------------|------------------|----------------------------|----------------------|
| Learning       | Tutorials        | Acquires their craft       | Informs action       |
| Goals          | How-to Guides    | Applies their craft        | Informs action       |
| Information    | Reference        | Applies their craft        | Informs cognition    |
| Understanding  | Explanation      | Acquires their craft       | Informs cognition    |

It can also be viewed as two separate axes:

[![diataxis four axes](https://diataxis.fr/_images/diataxis.png)](https://diataxis.fr/)

This is how four types of documentation were established. Daniele didn’t arbitrarily choose three or four categories and then shape the system around his narrative. Instead, he investigated what users require to learn a skill and built the system based on those findings.

## Why does it work?

The system works by making the expectations clear to both the documentation author and the reader. Each type of documentation has a clear and singular purpose, so the author knows what they are writing and when they are done. The reader, in turn, knows what to anticipate while navigating the documentation, avoiding the need to filter through irrelevant content.

When clear distinctions between different documentation types are not established, the content often becomes a hodgepodge. Diátaxis refers to this as [blur][diátaxis-blur], whereas we lean towards The Documentation System's wording, the [tendency to collapse][divio-documentation-system-tendency-to-collapse]. Given that there is overlap between various documentation types and user needs, it’s easy for authors to mix these types up.

Check out this visual from the [Diátaxis blur][diátaxis-blur] illustrating the shared roles among the different documentation types:

| Role                            | Type 1              | Type 2             |
|---------------------------------|---------------------|--------------------|
| Guide action                    | Tutorials           | How-to guides      |
| Serve the application of skill  | Reference           | How-to guides      |
| Contain propositional knowledge | Reference           | Explanation        |
| Serve the acquisition of skill  | Tutorials           | Explanation        |

You can easily see how each type of documentation shares some of the same roles. Although authors may strive to differentiate between the various types, a lack of clear boundaries will inevitably lead to overlap. Calling out the type of documentation you are writing and the purpose of it helps to keep the documentation clean and focused.

## Related resources

* [Writing Great Documentation: What to Write by Jacob Kaplan-Moss](https://jacobian.org/2009/nov/10/what-to-write/) - The original inspiration for Diátaxis
* [What nobody tells you about documentation (video)](https://pyvideo.org/pycon-au-2017/what-nobody-tells-you-about-documentation.html) - Daniele Procida's talk on the system
* [Documentation as a way to build Community](https://labs.quansight.org/blog/2020/03/documentation-as-a-way-to-build-community) - A blog post on why organized documentation is important
* Examples
  * [Django Documentation](https://django.readthedocs.io/en/stable/index.html#how-the-documentation-is-organized)
  * [Pytest Documentation](https://docs.pytest.org/en/stable/)

[daniele-procida]: https://vurt.org/
[diátaxis]: https://diataxis.fr/
[diátaxis-blur]: https://diataxis.fr/map/#blur
[diátaxis-explanation]: https://diataxis.fr/explanation/
[diátaxis-expectations-and-guidance]: https://diataxis.fr/map/#expectations-and-guidance
[diátaxis-foundations]: https://diataxis.fr/foundations/#
[diátaxis-how-to-guides]: https://diataxis.fr/how-to-guides/
[diátaxis-reference]: https://diataxis.fr/reference/
[diátaxis-tutorials]: https://diataxis.fr/tutorials/
[divio-documentation-system]: https://docs.divio.com/documentation-system/
[divio-documentation-system-tendency-to-collapse]: https://docs.divio.com/documentation-system/structure/#the-tendency-to-collapse
