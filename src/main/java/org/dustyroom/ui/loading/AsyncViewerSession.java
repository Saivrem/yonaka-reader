package org.dustyroom.ui.loading;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.iterators.ImageIterator;
import org.dustyroom.be.iterators.ImageIteratorFactory;
import org.dustyroom.be.models.animation.AnimatedPage;
import org.dustyroom.be.models.animation.AnimationClip;
import org.dustyroom.be.models.book.PageKey;
import org.dustyroom.be.models.book.PageRef;
import org.dustyroom.be.models.book.PageShape;
import org.dustyroom.be.models.book.Picture;
import org.dustyroom.be.models.direction.ReadingMode;
import org.dustyroom.be.models.direction.Direction;
import org.dustyroom.be.models.state.BookState;
import org.dustyroom.be.models.state.FitMode;
import org.dustyroom.be.models.state.ReaderSettings;
import org.dustyroom.be.models.state.VolumeBoundary;
import org.dustyroom.be.models.view.RecentBook;
import org.dustyroom.be.models.view.ViewerFrame;
import org.dustyroom.be.persistence.ReaderStore;
import org.dustyroom.be.utils.FileUtils;
import org.dustyroom.ui.navigation.SpreadPlanner;
import org.dustyroom.ui.rendering.SpreadRenderer;
import org.dustyroom.ui.rendering.ThumbnailRenderer;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.io.File;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.dustyroom.be.models.direction.ReadingMode.COMICS;
import static org.dustyroom.be.models.direction.ReadingMode.MANGA;
import static org.dustyroom.ui.loading.TaskPriority.INTERACTIVE;
import static org.dustyroom.ui.loading.TaskPriority.PREFETCH;

public final class AsyncViewerSession implements AutoCloseable {
    static final int DEFAULT_CACHE_ENTRIES = 12;
    static final long DEFAULT_CACHE_BYTES = 256L * 1024 * 1024;

    private final ViewerView view;
    private final ReaderStore store;
    private final Function<File, ImageIterator> sourceFactory;
    private final ViewerTaskExecutor tasks;
    private final Executor uiExecutor;
    private final BoundedImageCache cache;
    private final SpreadPlanner spreadPlanner;
    private final SpreadRenderer spreadRenderer;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong requestSequence = new AtomicLong();
    private final AtomicLong latestRequest = new AtomicLong();
    private volatile long displayedRequest = -1;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object closeMonitor = new Object();
    private final List<Runnable> closeCallbacks = new ArrayList<>();
    private final Map<PageKey, PageShape> shapes = new HashMap<>();
    private final AtomicLong recentRequest = new AtomicLong();
    private boolean persistenceWarningShown;
    private boolean cleanupComplete;
    private boolean closeCallbackDrainScheduled;
    // The following state is owned by the serial task executor.
    private ImageIterator source;
    private List<PageRef> pages = List.of();
    private int pageIndex = -1;
    private VolumeBoundary volumeBoundary = ReaderSettings.defaults().getVolumeBoundary();
    private boolean defaultTwoPageMode;
    private ReadingMode defaultReadingMode = MANGA;
    private FitMode defaultFitMode = FitMode.FIT_SCREEN;
    private boolean twoPageMode;
    private boolean shiftedPairing;
    private ReadingMode readingMode = MANGA;
    private String lastSourceError;
    private String lastRememberedSource;
    private volatile boolean groupRecentByFolder = true;
    private String removedActiveSource;

    public AsyncViewerSession(ViewerView view) {
        this(view, ReaderStore.disabled());
    }

    public AsyncViewerSession(ViewerView view, ReaderStore store) {
        this(
                view,
                ImageIteratorFactory::create,
                new SerialPriorityExecutor(),
                SwingUtilities::invokeLater,
                new BoundedImageCache(DEFAULT_CACHE_ENTRIES, DEFAULT_CACHE_BYTES),
                new SpreadPlanner(),
                new SpreadRenderer(),
                store
        );
    }

    public AsyncViewerSession(
            ViewerView view,
            Function<File, ImageIterator> sourceFactory,
            ViewerTaskExecutor tasks,
            Executor uiExecutor,
            BoundedImageCache cache,
            SpreadPlanner spreadPlanner,
            SpreadRenderer spreadRenderer
    ) {
        this(view, sourceFactory, tasks, uiExecutor, cache, spreadPlanner, spreadRenderer, ReaderStore.disabled());
    }

    public AsyncViewerSession(ViewerView view, Function<File, ImageIterator> sourceFactory,
                              ViewerTaskExecutor tasks, Executor uiExecutor, BoundedImageCache cache,
                              SpreadPlanner spreadPlanner, SpreadRenderer spreadRenderer, ReaderStore store) {
        this.view = view;
        this.store = store;
        this.sourceFactory = sourceFactory;
        this.tasks = tasks;
        this.uiExecutor = uiExecutor;
        this.cache = cache;
        this.spreadPlanner = spreadPlanner;
        this.spreadRenderer = spreadRenderer;
    }

    private static int restoredIndex(BookState saved, List<PageRef> pages) {
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).getKey().pageId().equals(saved.getPageId())) {
                return index;
            }
        }
        return Math.clamp(saved.getPageNumber() - 1, 0, Math.max(0, pages.size() - 1));
    }

    public void open(File file) {
        if (closed.get()) {
            return;
        }
        long targetGeneration = generation.incrementAndGet();
        long requestId = nextRequest();
        postLoading(targetGeneration, requestId);
        tasks.execute(INTERACTIVE, () -> openOnWorker(file, targetGeneration, requestId, false));
    }

    public void unload() {
        if (closed.get()) {
            return;
        }
        generation.incrementAndGet();
        nextRequest();
        // Saves already queued for displayed frames run before cleanup on the same worker.
        tasks.execute(INTERACTIVE, this::unloadOnWorker);
    }

    public void resetAndClose(Runnable afterReset, Consumer<String> onFailure) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // Invalidate already posted frames and prevent new saves while the reset is queued.
        generation.incrementAndGet();
        latestRequest.set(requestSequence.incrementAndGet());
        boolean queued = tasks.execute(INTERACTIVE, () -> {
            try {
                store.reset();
            } catch (RuntimeException exception) {
                closed.set(false);
                uiExecutor.execute(() -> onFailure.accept(I18n.text("reset.failed")));
                return;
            }
            tasks.close();
            try {
                closeOnWorker();
            } finally {
                uiExecutor.execute(afterReset);
            }
        });
        if (!queued) {
            closed.set(false);
            uiExecutor.execute(() -> onFailure.accept(I18n.text("reset.startFailed")));
        }
    }

    public void remember(ViewerFrame frame) {
        if (closed.get() || frame.getPageKey() == null) {
            return;
        }
        BookState state = new BookState(frame.getPageKey().sourceId(), frame.getPageKey().pageId(),
                frame.getFirstPage(), frame.getPageCount(), frame.isTwoPageMode(), frame.getReadingMode())
                .setShiftedPairing(frame.isShiftedPairing());
        tasks.execute(INTERACTIVE, () -> {
            if (state.getSourceId().equals(removedActiveSource)) {
                return;
            }
            persist(() -> store.saveBook(state));
            if (!state.getSourceId().equals(lastRememberedSource)) {
                lastRememberedSource = state.getSourceId();
                refreshRecentBooks();
            }
        });
    }

    public void setGroupRecentByFolder(boolean grouped) {
        groupRecentByFolder = grouped;
        refreshRecentBooks();
    }

    public void removeRecentBooks(List<String> sourceIds) {
        if (closed.get()) {
            return;
        }
        List<String> ids = List.copyOf(sourceIds);
        recentRequest.incrementAndGet();
        tasks.execute(INTERACTIVE, () -> {
            if (closed.get()) {
                return;
            }
            persist(() -> {
                store.removeRecent(ids);
                if (!pages.isEmpty() && ids.contains(pages.getFirst().getKey().sourceId())) {
                    removedActiveSource = pages.getFirst().getKey().sourceId();
                }
            });
            refreshRecentBooks();
        });
    }

    public void refreshRecentBooks() {
        if (closed.get()) {
            return;
        }
        long request = recentRequest.incrementAndGet();
        tasks.execute(INTERACTIVE, () -> {
            if (!recentActive(request)) {
                return;
            }
            List<RecentBook> books;
            try {
                books = RecentGroups.select(store.recentBooks(1000), groupRecentByFolder)
                        .stream()
                        .map(RecentBookLoader::inspectGroup)
                        .toList();
            } catch (RuntimeException exception) {
                warnPersistence();
                return;
            }
            List<RecentBook> snapshots = new ArrayList<>(books);
            postRecent(request, snapshots);
            // One cover per low-priority task: page navigation can run between covers.
            for (int index = 0; index < books.size(); index++) {
                int position = index;
                tasks.execute(TaskPriority.THUMBNAIL, () -> {
                    if (!recentActive(request)) {
                        return;
                    }
                    snapshots.set(position, RecentBookLoader.cover(books.get(position)));
                    postRecent(request, snapshots);
                });
            }
        });
    }

    private boolean recentActive(long request) {
        return !closed.get() && recentRequest.get() == request;
    }

    private void postRecent(long request, List<RecentBook> books) {
        List<RecentBook> snapshot = List.copyOf(books);
        uiExecutor.execute(() -> {
            if (recentActive(request)) {
                view.showRecentBooks(snapshot);
            }
        });
    }

    /**
     * Library work shares the store's worker and is cancelled by application reset/close.
     */
    public <T> void libraryTask(Function<ReaderStore, T> operation, Consumer<T> success, Consumer<String> failure) {
        if (closed.get()) {
            return;
        }
        tasks.execute(TaskPriority.THUMBNAIL, () -> {
            if (closed.get()) {
                return;
            }
            try {
                T result = operation.apply(store);
                uiExecutor.execute(() -> {
                    if (!closed.get()) {
                        success.accept(result);
                    }
                });
            } catch (RuntimeException exception) {
                uiExecutor.execute(() -> {
                    if (!closed.get()) {
                        failure.accept(exception.getMessage());
                    }
                });
            }
        });
    }

    public void configureDefaults(ReaderSettings settings) {
        ReaderSettings snapshot = settings.toBuilder().build();
        if (!closed.get()) {
            tasks.execute(INTERACTIVE, () -> {
                defaultTwoPageMode = snapshot.isDefaultTwoPageMode();
                defaultReadingMode = snapshot.getDefaultReadingMode();
                defaultFitMode = snapshot.getDefaultFitMode();
                volumeBoundary = snapshot.getVolumeBoundary();
            });
        }
    }

    public void saveSettings(ReaderSettings settings, Runnable success, Consumer<String> failure) {
        ReaderSettings snapshot = settings.toBuilder().build();
        if (closed.get()) {
            failure.accept(I18n.text("ui.the_reader_is_closing"));
            return;
        }
        tasks.execute(INTERACTIVE, () -> {
            if (closed.get()) {
                return;
            }
            try {
                store.saveSettings(snapshot);
                uiExecutor.execute(() -> {
                    if (!closed.get()) {
                        success.run();
                    }
                });
            } catch (RuntimeException exception) {
                uiExecutor.execute(() -> {
                    if (!closed.get()) {
                        failure.accept(I18n.text("errors.saveSettings", exception.getMessage()));
                    }
                });
            }
        });
    }

    public void saveSettings(ReaderSettings settings) {
        if (!closed.get()) {
            tasks.execute(INTERACTIVE, () -> persist(() -> store.saveSettings(settings)));
        }
    }

    private void persist(Runnable write) {
        try {
            write.run();
        } catch (RuntimeException exception) {
            warnPersistence();
        }
    }

    private Optional<BookState> savedBook(String sourceId) {
        try {
            return store.book(sourceId);
        } catch (RuntimeException exception) {
            warnPersistence();
            return Optional.empty();
        }
    }

    private void warnPersistence() {
        if (persistenceWarningShown) {
            return;
        }
        persistenceWarningShown = true;
        uiExecutor.execute(() -> {
            if (!closed.get()) {
                view.showPersistenceWarning(I18n.text("reading.saveFailed"));
            }
        });
    }

    /**
     * Uses the source only on its owning worker, below reading and prefetch priority.
     */
    public void thumbnail(int index, PageRef expected, Consumer<BufferedImage> result) {
        long targetGeneration = generation.get();
        boolean queued = !closed.get() && tasks.execute(TaskPriority.THUMBNAIL, () -> {
            BufferedImage thumbnail = null;
            if (isActive(targetGeneration) && index >= 0 && index < pages.size()
                    && pages.get(index).equals(expected)) {
                try {
                    BufferedImage image = cache.get(expected.getKey());
                    if (image == null) {
                        image = decodeImage(source, expected);
                    }
                    thumbnail = ThumbnailRenderer.render(image);
                } catch (RuntimeException ignored) { /* An unreadable preview must not interrupt reading. */ }
            }
            BufferedImage completed = thumbnail;
            uiExecutor.execute(() -> result.accept(isActive(targetGeneration) ? completed : null));
        });
        if (!queued) {
            uiExecutor.execute(() -> result.accept(null));
        }
    }

    public void next() {
        turnPage(Direction.NEXT);
    }

    public void previous() {
        turnPage(Direction.PREV);
    }

    public void first() {
        navigate(() -> pageIndex = 0);
    }

    public void last() {
        navigate(() -> pageIndex = pages.size() - 1);
    }

    public void goToPage(int pageNumber) {
        navigate(() -> {
            if (pageNumber < 1 || pageNumber > pages.size()) {
                throw new IllegalArgumentException(I18n.text("errors.pageRange", pages.size()));
            }
            pageIndex = pageNumber - 1;
        });
    }

    public void nextVolume() {
        switchVolume(true);
    }

    public void previousVolume() {
        switchVolume(false);
    }

    public void toggleTwoPageMode() {
        updatePresentation(() -> twoPageMode = !twoPageMode);
    }

    public void shiftPairing() {
        updatePresentation(() -> {
            shiftedPairing = !shiftedPairing;
            pageIndex = spreadAnchor(pageIndex);
        });
    }

    public void setComicsReadingMode() {
        updatePresentation(() -> readingMode = COMICS);
    }

    public void setMangaReadingMode() {
        updatePresentation(() -> readingMode = MANGA);
    }

    @Override
    public void close() {
        close(null);
    }

    public void close(Runnable afterClose) {
        boolean initiateClose;
        boolean scheduleCallbackDrain = false;
        synchronized (closeMonitor) {
            if (afterClose != null) {
                closeCallbacks.add(afterClose);
                if (cleanupComplete && !closeCallbackDrainScheduled) {
                    closeCallbackDrainScheduled = true;
                    scheduleCallbackDrain = true;
                }
            }

            initiateClose = closed.compareAndSet(false, true);
        }

        if (scheduleCallbackDrain) {
            uiExecutor.execute(this::drainCloseCallbacks);
        }
        if (!initiateClose) {
            return;
        }

        generation.incrementAndGet();
        latestRequest.set(requestSequence.incrementAndGet());
        boolean cleanupQueued = tasks.execute(INTERACTIVE, this::closeOnWorker);
        tasks.close();
        if (!cleanupQueued) {
            closeOnWorker();
        }
    }

    private void unloadOnWorker() {
        try {
            closeSource(source);
        } finally {
            source = null;
            pages = List.of();
            shapes.clear();
            cache.clear();
            pageIndex = -1;
            displayedRequest = -1;
            lastSourceError = null;
            lastRememberedSource = null;
            removedActiveSource = null;
        }
    }

    private void closeOnWorker() {
        try {
            unloadOnWorker();
        } finally {
            try {
                store.close();
            } finally {
                completeClose();
            }
        }
    }

    private void completeClose() {
        boolean scheduleCallbackDrain = false;
        synchronized (closeMonitor) {
            if (cleanupComplete) {
                return;
            }
            cleanupComplete = true;
            if (!closeCallbacks.isEmpty() && !closeCallbackDrainScheduled) {
                closeCallbackDrainScheduled = true;
                scheduleCallbackDrain = true;
            }
        }
        if (scheduleCallbackDrain) {
            uiExecutor.execute(this::drainCloseCallbacks);
        }
    }

    private void drainCloseCallbacks() {
        while (true) {
            Runnable callback;
            synchronized (closeMonitor) {
                if (closeCallbacks.isEmpty()) {
                    closeCallbackDrainScheduled = false;
                    return;
                }
                callback = closeCallbacks.removeFirst();
            }
            try {
                callback.run();
            } catch (RuntimeException ignored) {
                // One shutdown callback must not prevent the remaining callbacks.
            }
        }
    }

    private void openOnWorker(File file, long targetGeneration, long requestId, boolean fromEnd) {
        if (!isActive(targetGeneration)) {
            return;
        }

        ImageIterator candidate = null;
        try {
            candidate = sourceFactory.apply(file);
            if (!isActive(targetGeneration)) {
                closeCandidate(candidate);
                return;
            }

            List<PageRef> candidatePages = List.copyOf(candidate.pages());
            if (candidatePages.isEmpty()) {
                throw new IllegalStateException(I18n.text("ui.image_source_contains_no_pages"));
            }
            int candidateIndex = fromEnd ? candidatePages.size() - 1 : candidate.initialPageIndex();
            if (candidateIndex < 0 || candidateIndex >= candidatePages.size()) {
                throw new IllegalStateException(I18n.text("ui.image_source_has_an_invalid_initial_page"));
            }
            Optional<BookState> saved = savedBook(candidatePages.getFirst().getKey().sourceId());
            // An explicitly selected image takes precedence over the saved position of its folder.
            if (saved.isPresent() && !FileUtils.isImage.test(file)) {
                candidateIndex = restoredIndex(saved.get(), candidatePages);
            }
            PageRef initialPage = candidatePages.get(candidateIndex);
            BufferedImage initialImage = decodeImage(candidate, initialPage);
            if (!isActive(targetGeneration)) {
                closeCandidate(candidate);
                return;
            }

            ImageIterator previousSource = source;
            source = candidate;
            candidate = null;
            pages = candidatePages;
            pageIndex = candidateIndex;
            shiftedPairing = saved.map(BookState::isShiftedPairing).orElse(false);
            twoPageMode = saved.map(BookState::isTwoPageMode).orElse(defaultTwoPageMode);
            readingMode = saved.map(BookState::getReadingMode).orElse(defaultReadingMode);
            if (saved.isEmpty()) {
                FitMode selectedFit = defaultFitMode;
                uiExecutor.execute(() -> {
                    if (isLatest(targetGeneration, requestId)) {
                        view.useDefaultScale(selectedFit);
                    }
                });
            }
            shapes.clear();
            cache.clear();
            shapes.put(initialPage.getKey(), PageShape.from(initialImage));
            cache.put(initialPage.getKey(), initialImage);
            lastSourceError = null;
            if (previousSource != source) {
                closeSource(previousSource);
            }

            persist(() -> store.showRecent(initialPage.getKey().sourceId()));
            removedActiveSource = null;
            lastRememberedSource = null;
            renderAndPublish(targetGeneration, requestId, initialImage);
        } catch (RuntimeException e) {
            closeCandidate(candidate);
            lastSourceError = errorMessage(e);
            postError(targetGeneration, requestId, e);
        }
    }

    private void navigate(Runnable navigation) {
        submitInteractive((targetGeneration, requestId) -> {
            int previousIndex = pageIndex;
            try {
                navigation.run();
                renderAndPublish(targetGeneration, requestId);
            } catch (RuntimeException e) {
                pageIndex = previousIndex;
                throw e;
            }
        });
    }

    private void switchVolume(boolean next) {
        submitInteractive((targetGeneration, requestId) -> {
            PageRef selected = next
                    ? source.switchToNextVolume()
                    : source.switchToPreviousVolume();
            List<PageRef> switchedPages = List.copyOf(source.pages());
            if (selected == null || switchedPages.isEmpty()) {
                throw new IllegalStateException(I18n.text("ui.the_selected_volume_contains_no_pages"));
            }

            int selectedIndex = switchedPages.indexOf(selected);
            pages = switchedPages;
            pageIndex = selectedIndex < 0 ? source.initialPageIndex() : selectedIndex;
            shiftedPairing = false;
            savedBook(pages.getFirst().getKey().sourceId()).ifPresent(state -> {
                pageIndex = restoredIndex(state, pages);
                shiftedPairing = state.isShiftedPairing();
                twoPageMode = state.isTwoPageMode();
                readingMode = state.getReadingMode();
            });
            shapes.clear();
            cache.clear();
            persist(() -> store.showRecent(selected.getKey().sourceId()));
            removedActiveSource = null;
            lastRememberedSource = null;
            renderAndPublish(targetGeneration, requestId);
        });
    }

    private void updatePresentation(Runnable update) {
        submitInteractive((targetGeneration, requestId) -> {
            update.run();
            renderAndPublish(targetGeneration, requestId);
        });
    }

    private void submitInteractive(WorkerCommand command) {
        submitInteractive(true, command);
    }

    private void submitInteractive(boolean showLoading, WorkerCommand command) {
        if (closed.get()) {
            return;
        }
        long targetGeneration = generation.get();
        long requestId = nextRequest();
        tasks.execute(INTERACTIVE, () -> {
            if (!isActive(targetGeneration)) {
                return;
            }
            if (source == null || pages.isEmpty()) {
                if (lastSourceError != null) {
                    postError(
                            targetGeneration,
                            requestId,
                            new IllegalStateException(lastSourceError)
                    );
                }
                return;
            }
            if (showLoading) {
                postLoading(targetGeneration, requestId);
            }
            try {
                command.run(targetGeneration, requestId);
            } catch (RuntimeException e) {
                postError(targetGeneration, requestId, e);
            }
        });
    }

    private void turnPage(Direction direction) {
        long previousRequest = latestRequest.get();
        submitInteractive(false, (targetGeneration, requestId) -> {
            int original = pageIndex;
            int next = pageIndex + 1;
            if (direction == Direction.NEXT && resolveSecondPage(pageIndex) != null) {
                next++;
            }
            boolean boundary = direction == Direction.NEXT ? next >= pages.size() : pageIndex == 0;
            boolean showHint = boundary && volumeBoundary == VolumeBoundary.STOP;
            if (boundary && volumeBoundary != VolumeBoundary.WRAP) {
                if (volumeBoundary == VolumeBoundary.NEXT_VOLUME) {
                    File adjacent = source.adjacentVolume(direction);
                    if (adjacent != null) {
                        postLoading(targetGeneration, requestId);
                        openOnWorker(adjacent, targetGeneration, requestId, direction == Direction.PREV);
                        return;
                    }
                }
                // Keep the displayed page, scroll and GIF timeline on an ignored keypress.
                // A previous turn still awaiting display must publish its frame instead.
                if (displayedRequest == previousRequest && latestRequest.compareAndSet(requestId, previousRequest)) {
                    if (showHint) {
                        postBoundaryHint(targetGeneration, previousRequest, direction);
                    }
                    return;
                }
            } else if (direction == Direction.NEXT) {
                pageIndex = boundary ? 0 : next;
            } else {
                movePrevious();
            }

            try {
                postLoading(targetGeneration, requestId);
                renderAndPublish(targetGeneration, requestId);
                if (showHint) {
                    postBoundaryHint(targetGeneration, requestId, direction);
                }
            } catch (RuntimeException error) {
                pageIndex = original;
                throw error;
            }
        });
    }

    private void movePrevious() {
        if (twoPageMode && (shiftedPairing || pageIndex == 0)) {
            pageIndex = spreadAnchor(Math.floorMod(pageIndex - 1, pages.size()));
            return;
        }
        int pageCount = pages.size();
        for (int distance = 1; distance <= Math.min(2, pageCount); distance++) {
            int candidate = Math.floorMod(pageIndex - distance, pageCount);
            int step = resolveSecondPage(candidate) == null ? 1 : 2;
            if (Math.floorMod(candidate + step, pageCount) == pageIndex) {
                pageIndex = candidate;
                return;
            }
        }
        pageIndex = Math.floorMod(pageIndex - 1, pageCount);
    }

    private void renderAndPublish(long targetGeneration, long requestId) {
        renderAndPublish(targetGeneration, requestId, null);
    }

    private void renderAndPublish(long targetGeneration, long requestId, BufferedImage initialImage) {
        if (twoPageMode && shiftedPairing) {
            int anchor = spreadAnchor(pageIndex);
            if (anchor != pageIndex) {
                pageIndex = anchor;
                initialImage = null;
            }
        }
        if (!isLatest(targetGeneration, requestId)) {
            return;
        }
        PageRef current = pages.get(pageIndex);
        BufferedImage currentImage = initialImage != null ? initialImage : loadImage(current);
        if (!isLatest(targetGeneration, requestId)) {
            return;
        }
        PageRef second = resolveSecondPage(pageIndex);
        if (!isLatest(targetGeneration, requestId)) {
            return;
        }
        BufferedImage secondImage = second == null ? null : loadImage(second);
        if (!isLatest(targetGeneration, requestId)) {
            return;
        }

        BufferedImage composed = spreadRenderer.compose(
                currentImage,
                secondImage,
                readingMode == MANGA
        );
        if (!isLatest(targetGeneration, requestId)) {
            return;
        }
        ViewerFrame frame = new ViewerFrame(
                composed,
                spreadRenderer.buildTitle(current.getMetadata(), second),
                current.getMetadata().getDir(),
                pageIndex + 1, pageIndex + (second == null ? 1 : 2), pages.size(),
                twoPageMode, readingMode, current.getKey()
        ).setShiftedPairing(shiftedPairing);
        postFrame(targetGeneration, requestId, frame);
        scheduleAnimations(targetGeneration, requestId, current, second, currentImage, secondImage, readingMode == MANGA);
        schedulePrefetch(targetGeneration, requestId, pageIndex, second != null);
    }

    private static boolean isGif(PageRef page) {
        return page.getKey().pageId().toLowerCase(Locale.ROOT).endsWith(".gif");
    }

    private void scheduleAnimations(long targetGeneration, long requestId, PageRef first, PageRef second,
                                    BufferedImage firstImage, BufferedImage secondImage, boolean manga) {
        List<PageRef> refs = second == null ? List.of(first) : List.of(first, second);
        if (!isGif(first) && (second == null || !isGif(second))) {
            return;
        }
        tasks.execute(PREFETCH, () -> {
            if (!isLatest(targetGeneration, requestId)) {
                return;
            }
            List<Rectangle> bounds = spreadRenderer.layout(firstImage, secondImage, manga);

            List<AnimatedPage> animated = new ArrayList<>();
            for (int index = 0; index < refs.size(); index++) {
                if (!isLatest(targetGeneration, requestId)) {
                    return;
                }
                PageRef page = refs.get(index);
                if (!isGif(page)) {
                    continue;
                }
                try {
                    AnimationClip clip = source.animation(page);
                    if (clip == null) {
                        continue;
                    }
                    Rectangle placement = bounds.get(index);
                    animated.add(new AnimatedPage(clip, placement.x, placement.y,
                            placement.width, placement.height, second != null));
                } catch (RuntimeException exception) {
                    postError(targetGeneration, requestId, exception);
                }
            }
            uiExecutor.execute(() -> {
                if (isLatest(targetGeneration, requestId)) {
                    view.showAnimations(first.getKey(), List.copyOf(animated));
                }
            });
        });
    }

    private int spreadAnchor(int index) {
        if (index == 0 || spreadPlanner.startsPair(index, shiftedPairing)) {
            return index;
        }
        return resolveSecondPage(index - 1) == null ? index : index - 1;
    }

    private PageRef resolveSecondPage(int anchorIndex) {
        if (!twoPageMode || pages.size() < 2 || anchorIndex + 1 >= pages.size()) {
            return null;
        }
        if (shiftedPairing && !spreadPlanner.startsPair(anchorIndex, true)) {
            return null;
        }
        PageRef current = pages.get(anchorIndex);
        PageRef next = pages.get(anchorIndex + 1);
        return spreadPlanner.shouldPair(shapeOf(current), () -> shapeOf(next)) ? next : null;
    }

    private PageShape shapeOf(PageRef page) {
        PageShape shape = shapes.get(page.getKey());
        if (shape != null) {
            return shape;
        }
        return PageShape.from(loadImage(page));
    }

    private BufferedImage loadImage(PageRef page) {
        BufferedImage cached = cache.get(page.getKey());
        if (cached != null) {
            shapes.putIfAbsent(page.getKey(), PageShape.from(cached));
            return cached;
        }

        BufferedImage image = decodeImage(source, page);
        shapes.put(page.getKey(), PageShape.from(image));
        cache.put(page.getKey(), image);
        return image;
    }

    private BufferedImage decodeImage(ImageIterator imageSource, PageRef page) {
        Picture loaded = imageSource.load(page);
        if (loaded == null || loaded.getImage() == null) {
            throw new IllegalStateException(I18n.text("ui.image_source_returned_an_empty_page"));
        }
        return loaded.getImage();
    }

    private void schedulePrefetch(
            long targetGeneration,
            long requestId,
            int anchorIndex,
            boolean spreadVisible
    ) {
        if (!isLatest(targetGeneration, requestId)) {
            return;
        }
        Set<PageRef> targets = new LinkedHashSet<>();
        int[] offsets = spreadVisible ? new int[]{2, 3} : new int[]{1};
        for (int offset : offsets) {
            if (pages.size() <= 1) {
                break;
            }
            PageRef candidate = pages.get(Math.floorMod(anchorIndex + offset, pages.size()));
            if (!candidate.equals(pages.get(anchorIndex))) {
                targets.add(candidate);
            }
        }
        if (targets.isEmpty()) {
            return;
        }

        List<PageRef> capturedTargets = new ArrayList<>(targets);
        tasks.execute(PREFETCH, () -> {
            if (!isLatest(targetGeneration, requestId)) {
                return;
            }
            for (PageRef target : capturedTargets) {
                if (!isLatest(targetGeneration, requestId)) {
                    return;
                }
                try {
                    loadImage(target);
                } catch (RuntimeException ignored) {
                    // Prefetch is opportunistic; interactive loading reports errors.
                }
            }
        });
    }

    private void postBoundaryHint(long targetGeneration, long requestId, Direction direction) {
        uiExecutor.execute(() -> {
            if (isLatest(targetGeneration, requestId)) {
                view.showBoundaryHint(direction == Direction.NEXT ? I18n.text("ui.last_page_label") : I18n.text("ui.first_page_label"));
            }
        });
    }

    private void postLoading(long targetGeneration, long requestId) {
        uiExecutor.execute(() -> {
            if (isLatest(targetGeneration, requestId)) {
                view.showLoading();
            }
        });
    }

    private void postFrame(long targetGeneration, long requestId, ViewerFrame frame) {
        List<PageRef> capturedPages = pages;
        uiExecutor.execute(() -> {
            if (isLatest(targetGeneration, requestId)) {
                view.showPages(capturedPages);
                displayedRequest = requestId;
                view.showFrame(frame);
            }
        });
    }

    private void postError(long targetGeneration, long requestId, RuntimeException error) {
        String finalMessage = errorMessage(error);
        uiExecutor.execute(() -> {
            if (isLatest(targetGeneration, requestId)) {
                view.showError(finalMessage);
            }
        });
    }

    private String errorMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? I18n.text("ui.unable_to_load_the_selected_image")
                : message;
    }

    private long nextRequest() {
        long requestId = requestSequence.incrementAndGet();
        latestRequest.set(requestId);
        return requestId;
    }

    private boolean isActive(long targetGeneration) {
        return !closed.get() && generation.get() == targetGeneration;
    }

    private boolean isLatest(long targetGeneration, long requestId) {
        return isActive(targetGeneration) && latestRequest.get() == requestId;
    }

    private void closeSource(ImageIterator iterator) {
        if (iterator != null) {
            iterator.close();
        }
    }

    private void closeCandidate(ImageIterator candidate) {
        if (candidate != source) {
            closeSource(candidate);
        }
    }

}
